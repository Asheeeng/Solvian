-- 用户表（登录/注册）
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_user_role ON app_user(role);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS class_id BIGINT;

-- 班级表（测试阶段使用单默认班级）
CREATE TABLE IF NOT EXISTS classes (
    id BIGSERIAL PRIMARY KEY,
    class_name VARCHAR(50) NOT NULL,
    teacher_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 兼容旧版本数据库：早期测试数据使用了字符串班级 ID，这里统一收敛为默认测试班级（id=1）。
UPDATE app_user
SET class_id = '1'
WHERE role = 'STUDENT'
  AND (class_id IS NULL OR class_id::text !~ '^[0-9]+$');

UPDATE app_user
SET class_id = NULL
WHERE role <> 'STUDENT'
  AND (class_id IS NULL OR class_id::text !~ '^[0-9]+$');

UPDATE classes
SET teacher_id = COALESCE(
    (
        SELECT u.id
        FROM app_user u
        WHERE u.user_id = classes.teacher_id::text
           OR u.username = classes.teacher_id::text
        ORDER BY u.id
        LIMIT 1
    ),
    NULLIF(classes.teacher_id::text, '')::bigint
)
WHERE teacher_id IS NOT NULL
  AND teacher_id::text !~ '^[0-9]+$';

DELETE FROM classes
WHERE id::text <> '1'
  AND id::text <> 'class_default_test';

DELETE FROM classes
WHERE id::text = '1'
  AND EXISTS (
      SELECT 1
      FROM classes c2
      WHERE c2.id::text = 'class_default_test'
  );

UPDATE classes
SET id = '1',
    class_name = '默认测试班级'
WHERE id::text = 'class_default_test';

ALTER TABLE app_user ALTER COLUMN class_id DROP DEFAULT;
ALTER TABLE app_user
    ALTER COLUMN class_id TYPE BIGINT
    USING CASE
        WHEN role = 'STUDENT'
            THEN COALESCE(
                CASE
                    WHEN class_id IS NULL OR class_id::text = '' OR class_id::text !~ '^[0-9]+$' THEN '1'
                    ELSE class_id::text
                END,
                '1'
            )::bigint
        ELSE CASE
            WHEN class_id IS NULL OR class_id::text = '' OR class_id::text !~ '^[0-9]+$' THEN NULL
            ELSE class_id::text::bigint
        END
    END;

ALTER TABLE classes
    ALTER COLUMN id TYPE BIGINT
    USING NULLIF(id::text, '')::bigint;

ALTER TABLE classes
    ALTER COLUMN teacher_id TYPE BIGINT
    USING CASE
        WHEN teacher_id IS NULL OR teacher_id::text = '' THEN NULL
        ELSE teacher_id::text::bigint
    END;

CREATE SEQUENCE IF NOT EXISTS classes_id_seq AS BIGINT START WITH 1 INCREMENT BY 1;
SELECT setval('classes_id_seq', COALESCE((SELECT MAX(id) FROM classes), 1), true);
ALTER TABLE classes ALTER COLUMN id SET DEFAULT nextval('classes_id_seq');

UPDATE classes
SET teacher_id = COALESCE(
    (SELECT id FROM app_user WHERE username = 'tea123' LIMIT 1),
    (SELECT id FROM app_user WHERE username = 'teacher_demo' LIMIT 1),
    teacher_id
)
WHERE id = 1;

CREATE INDEX IF NOT EXISTS idx_classes_teacher_id ON classes(teacher_id);

-- 登录会话表（替代内存 token 存储）
CREATE TABLE IF NOT EXISTS app_session (
    token VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_app_session_user_id ON app_session(user_id);

-- 诊断记录表（为后续把历史/看板迁移到数据库预留）
CREATE TABLE IF NOT EXISTS diagnosis_record (
    record_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    class_id BIGINT,
    submission_id BIGINT,
    status VARCHAR(32),
    steps_json TEXT,
    feedback TEXT,
    error_index INTEGER,
    tags_json TEXT,
    is_socratic BOOLEAN DEFAULT TRUE,
    problem_type VARCHAR(64) DEFAULT 'matrix',
    image_name VARCHAR(255),
    ai_feedback VARCHAR(32),
    error_type VARCHAR(32),
    note TEXT,
    math_data_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_user_id ON diagnosis_record(user_id);
CREATE INDEX IF NOT EXISTS idx_diagnosis_created_at ON diagnosis_record(created_at DESC);

-- 兼容旧版本数据库：历史记录查询会读取这些字段，缺失时需要补齐。
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS class_id BIGINT;
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS submission_id BIGINT;
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS is_socratic BOOLEAN DEFAULT TRUE;
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS problem_type VARCHAR(64) DEFAULT 'matrix';
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS image_name VARCHAR(255);
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS ai_feedback VARCHAR(32);
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS error_type VARCHAR(32);
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS note TEXT;
ALTER TABLE diagnosis_record ADD COLUMN IF NOT EXISTS math_data_json TEXT;

-- 异步诊断任务表（新链路：创建任务后后台执行视觉/推理）
CREATE TABLE IF NOT EXISTS diagnosis_task (
    task_id VARCHAR(64) PRIMARY KEY,
    record_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    class_id BIGINT,
    submission_id BIGINT,
    target_user_id VARCHAR(64),
    target_username VARCHAR(64),
    target_role VARCHAR(20),
    status VARCHAR(32) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    stage_message VARCHAR(255),
    input_image_name VARCHAR(255),
    input_image_path VARCHAR(512),
    input_image_hash VARCHAR(128),
    input_content_type VARCHAR(128),
    input_file_size BIGINT,
    subject_scope VARCHAR(64) DEFAULT 'matrix',
    is_socratic BOOLEAN DEFAULT TRUE,
    vision_model VARCHAR(64),
    reasoning_model VARCHAR(64),
    vision_result_json TEXT,
    partial_result_json TEXT,
    final_result_json TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_diagnosis_task_user_id ON diagnosis_task(user_id);
CREATE INDEX IF NOT EXISTS idx_diagnosis_task_status ON diagnosis_task(status);
CREATE INDEX IF NOT EXISTS idx_diagnosis_task_hash ON diagnosis_task(input_image_hash);
CREATE INDEX IF NOT EXISTS idx_diagnosis_task_created_at ON diagnosis_task(created_at DESC);
ALTER TABLE diagnosis_task ADD COLUMN IF NOT EXISTS class_id BIGINT;
ALTER TABLE diagnosis_task ADD COLUMN IF NOT EXISTS submission_id BIGINT;
ALTER TABLE diagnosis_task ADD COLUMN IF NOT EXISTS target_user_id VARCHAR(64);
ALTER TABLE diagnosis_task ADD COLUMN IF NOT EXISTS target_username VARCHAR(64);
ALTER TABLE diagnosis_task ADD COLUMN IF NOT EXISTS target_role VARCHAR(20);

-- 学生作业提交表（学生上传、老师查看、AI批改的业务主表）
CREATE TABLE IF NOT EXISTS submissions (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    student_user_id VARCHAR(64) NOT NULL,
    student_name VARCHAR(64) NOT NULL,
    class_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    image_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128),
    file_size BIGINT,
    check_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    check_result_json TEXT,
    diagnosis_record_id VARCHAR(64),
    submit_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_at TIMESTAMPTZ
);

ALTER TABLE submissions ADD COLUMN IF NOT EXISTS legacy_id VARCHAR(128);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS image_name VARCHAR(255);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS image_content_type VARCHAR(128);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS display_name VARCHAR(128);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS student_id BIGINT;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS student_user_id VARCHAR(64);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS student_name VARCHAR(64);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS class_id BIGINT;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS file_name VARCHAR(255);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS image_path VARCHAR(512);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS content_type VARCHAR(128);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS file_size BIGINT;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS check_status VARCHAR(32) DEFAULT 'PENDING';
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS check_result_json TEXT;
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS diagnosis_record_id VARCHAR(64);
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS submit_time TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE submissions ADD COLUMN IF NOT EXISTS checked_at TIMESTAMPTZ;

UPDATE submissions
SET legacy_id = COALESCE(legacy_id, id::text)
WHERE id IS NOT NULL;

UPDATE submissions s
SET student_id = COALESCE(
    (
        SELECT u.id
        FROM app_user u
        WHERE u.user_id = s.student_id::text
           OR u.username = s.student_id::text
        ORDER BY u.id
        LIMIT 1
    ),
    NULLIF(s.student_id::text, '')::bigint
)
WHERE student_id IS NOT NULL
  AND student_id::text !~ '^[0-9]+$';

UPDATE submissions
SET class_id = '1'
WHERE class_id IS NULL
   OR class_id::text !~ '^[0-9]+$';

UPDATE submissions
SET student_user_id = COALESCE(student_user_id, display_name, student_name)
WHERE student_user_id IS NULL;

UPDATE submissions s
SET student_user_id = u.user_id
FROM app_user u
WHERE s.student_id IS NOT NULL
  AND s.student_id = u.id
  AND (s.student_user_id IS NULL OR s.student_user_id <> u.user_id);

UPDATE submissions
SET file_name = COALESCE(NULLIF(file_name, ''), NULLIF(image_name, ''), '未命名作业')
WHERE file_name IS NULL
   OR file_name = '';

UPDATE submissions
SET content_type = COALESCE(NULLIF(content_type, ''), NULLIF(image_content_type, ''), 'application/octet-stream')
WHERE content_type IS NULL
   OR content_type = '';

UPDATE submissions
SET display_name = COALESCE(display_name, student_name, student_user_id, '学生提交')
WHERE display_name IS NULL;

WITH submission_renumber AS (
    SELECT ctid,
           (
               COALESCE(
                   (
                       SELECT MAX(CASE WHEN id::text ~ '^[0-9]+$' THEN id::text::bigint END)
                       FROM submissions
                   ),
                   0
               ) + ROW_NUMBER() OVER (ORDER BY submit_time NULLS FIRST, ctid)
           )::text AS new_id
    FROM submissions
    WHERE id IS NOT NULL
      AND id::text !~ '^[0-9]+$'
)
UPDATE submissions s
SET id = submission_renumber.new_id::bigint
FROM submission_renumber
WHERE s.ctid = submission_renumber.ctid;

ALTER TABLE submissions
    ALTER COLUMN id TYPE BIGINT
    USING NULLIF(id::text, '')::bigint;

ALTER TABLE submissions
    ALTER COLUMN student_id TYPE BIGINT
    USING CASE
        WHEN student_id IS NULL OR student_id::text = '' OR student_id::text !~ '^[0-9]+$' THEN NULL
        ELSE student_id::text::bigint
    END;

ALTER TABLE submissions
    ALTER COLUMN class_id TYPE BIGINT
    USING CASE
        WHEN class_id IS NULL OR class_id::text = '' OR class_id::text !~ '^[0-9]+$' THEN 1
        ELSE class_id::text::bigint
    END;

CREATE SEQUENCE IF NOT EXISTS submissions_id_seq AS BIGINT START WITH 1 INCREMENT BY 1;
SELECT setval('submissions_id_seq', COALESCE((SELECT MAX(id) FROM submissions), 1), true);
ALTER TABLE submissions ALTER COLUMN id SET DEFAULT nextval('submissions_id_seq');
ALTER TABLE submissions ALTER COLUMN display_name DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_submissions_student_id ON submissions(student_id);
CREATE INDEX IF NOT EXISTS idx_submissions_class_id ON submissions(class_id);
CREATE INDEX IF NOT EXISTS idx_submissions_submit_time ON submissions(submit_time DESC);

-- 兼容历史 JSONB 列，统一转为 TEXT 以便轻量存储与后续迁移。
ALTER TABLE diagnosis_record
    ALTER COLUMN steps_json TYPE TEXT USING steps_json::text;
ALTER TABLE diagnosis_record
    ALTER COLUMN tags_json TYPE TEXT USING tags_json::text;
ALTER TABLE diagnosis_record
    ALTER COLUMN math_data_json TYPE TEXT USING math_data_json::text;

-- AI 反馈表（为后续统计与审计预留）
CREATE TABLE IF NOT EXISTS ai_feedback_record (
    id BIGSERIAL PRIMARY KEY,
    feedback_id VARCHAR(64) UNIQUE,
    record_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    ai_feedback VARCHAR(32) NOT NULL,
    error_type VARCHAR(32),
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_feedback_record_id ON ai_feedback_record(record_id);
CREATE INDEX IF NOT EXISTS idx_ai_feedback_created_at ON ai_feedback_record(created_at DESC);
ALTER TABLE ai_feedback_record ADD COLUMN IF NOT EXISTS feedback_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_feedback_feedback_id ON ai_feedback_record(feedback_id);

-- 前端事件日志表（埋点追踪预留）
CREATE TABLE IF NOT EXISTS event_record (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) UNIQUE,
    user_id VARCHAR(64),
    username VARCHAR(64),
    role VARCHAR(20),
    event_name VARCHAR(128) NOT NULL,
    event_type VARCHAR(128),
    page VARCHAR(255),
    action VARCHAR(255),
    record_id VARCHAR(64),
    payload_json TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_created_at ON event_record(created_at DESC);
ALTER TABLE event_record ADD COLUMN IF NOT EXISTS event_id VARCHAR(64);
ALTER TABLE event_record ADD COLUMN IF NOT EXISTS event_type VARCHAR(128);
ALTER TABLE event_record ADD COLUMN IF NOT EXISTS page VARCHAR(255);
ALTER TABLE event_record ADD COLUMN IF NOT EXISTS action VARCHAR(255);
ALTER TABLE event_record ADD COLUMN IF NOT EXISTS record_id VARCHAR(64);
ALTER TABLE event_record
    ALTER COLUMN payload_json TYPE TEXT USING payload_json::text;
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_record_event_id ON event_record(event_id);
