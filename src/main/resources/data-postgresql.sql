-- 初始化三角色演示账号（可直接登录）
-- 默认密码统一为：abc123456
INSERT INTO app_user (user_id, username, password, role)
VALUES
    ('u_student_demo', 'student_demo', 'abc123456', 'STUDENT'),
    ('u_teacher_demo', 'teacher_demo', 'abc123456', 'TEACHER'),
    ('u_admin_demo', 'admin_demo', 'abc123456', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

INSERT INTO classes (id, class_name, teacher_id)
SELECT 1,
       '默认测试班级',
       COALESCE(
           (SELECT id FROM app_user WHERE username = 'tea123' LIMIT 1),
           (SELECT id FROM app_user WHERE username = 'teacher_demo' LIMIT 1)
       )
WHERE COALESCE(
    (SELECT id FROM app_user WHERE username = 'tea123' LIMIT 1),
    (SELECT id FROM app_user WHERE username = 'teacher_demo' LIMIT 1)
) IS NOT NULL
ON CONFLICT (id) DO UPDATE
SET class_name = EXCLUDED.class_name,
    teacher_id = EXCLUDED.teacher_id;

UPDATE app_user
SET class_id = 1
WHERE role = 'STUDENT'
  AND class_id IS NULL;
