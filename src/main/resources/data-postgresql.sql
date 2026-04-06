-- 初始化三角色演示账号（可直接登录）
-- 默认密码统一为：abc123456
INSERT INTO app_user (user_id, username, password, role)
VALUES
    ('u_student_demo', 'student_demo', 'abc123456', 'STUDENT'),
    ('u_teacher_demo', 'teacher_demo', 'abc123456', 'TEACHER'),
    ('u_admin_demo', 'admin_demo', 'abc123456', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

