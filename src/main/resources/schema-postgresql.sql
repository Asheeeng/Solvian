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
