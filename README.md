# Solvian

基于 Java 17 + Spring Boot 3.2.5 的 AI 数学逻辑诊断系统可运行基础版。  
当前版本目标是“业务链路完整、可演示、易扩展”，暂不接数据库。

## 技术栈

- Java 17
- Spring Boot 3.2.5
- Maven
- MyBatis-Plus 3.5.6（预留扩展）
- PostgreSQL Driver（预留扩展）
- 前端：静态页面（HTML/CSS/ES Module）

## 当前已实现能力

- 注册/登录（角色：`STUDENT` / `TEACHER` / `ADMIN`）
- 首页两栏布局（左题目主区 2 / 右 AI 诊断区 1）
- 图片上传与预览，点击放大
- 启动 AI 诊断（`isSocratic` 开关）
- 两阶段模型链路：视觉提取（`glm-4.6v`）+ 推理分析（`glm-4.7`）
- 结果展示：`status`、`steps`、`feedback`、`errorIndex`、`tags`
- AI 识别反馈保存（准确/不准确 + 错误类型 + note）
- 错题本与统计面板（按钮触发抽屉）
- 前端事件上报
- PDF 报告下载接口（占位实现）
- PostgreSQL 持久化（认证、会话、诊断记录、AI反馈、事件日志）
- 对外接口保持稳定，便于后续继续扩展业务字段与权限能力
- 模型连通性测试接口（`GET /api/model/test`）

## 目录结构（核心）

```text
src/main/java/com/example/springbootbase
├── controller
├── service
│   └── impl
├── dto
│   ├── request
│   └── response
├── model
├── auth
├── config
├── store
├── util
└── vo
```

## 运行方式

1. 确保本机有 JDK 17。
2. 安装 Maven（或使用项目内 `.local/apache-maven-3.9.9/bin/mvn`）。
3. 启动：

```bash
./.local/apache-maven-3.9.9/bin/mvn spring-boot:run
```

4. 浏览器访问：

```text
http://localhost:8080
```

## 主要接口

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/evaluate`（multipart：`file`、`isSocratic`）
- `POST /api/log-event`
- `POST /api/ai-feedback`
- `GET /api/history`
- `GET /api/dashboard-summary`
- `GET /api/report/{recordId}/pdf`
- `GET /api/model/test`

## 配置说明

`application.yml` 中已预留：

- PostgreSQL 连接配置（当前阶段未启用持久化）
- 文件上传限制
- CORS 配置
- AI 模型配置（`app.ai.*`）

默认配置为：

- `spring.profiles.active=local`（可被环境变量覆盖）
- `app.ai.vision-model=glm-4.6v`
- `app.ai.model=glm-4.7`
- `app.ai.base-url=https://open.bigmodel.cn/api/paas/v4`
- `app.ai.mock-enabled=false`
- `app.ai.fallback-to-mock=false`

本地密钥建议放在 `application-local.yml` 或环境变量：

- `AI_MODEL_API_KEY`
- `AI_MODEL_BASE_URL`（可选）

安全要求：API Key 仅通过环境变量或配置注入，不允许硬编码。

## PostgreSQL 初始化（本地）

当前项目在 `local` 环境下支持 PostgreSQL 自动初始化：

- 表结构脚本：[schema-postgresql.sql](/Users/suis/Developer/Codex/Copilot/src/main/resources/schema-postgresql.sql)
- 初始化数据脚本：[data-postgresql.sql](/Users/suis/Developer/Codex/Copilot/src/main/resources/data-postgresql.sql)

默认会创建并预留这些表：

- `app_user`（登录注册）
- `diagnosis_record`（诊断历史）
- `ai_feedback_record`（识别反馈）
- `event_record`（前端事件日志）

默认演示账号（密码统一 `abc123456`）：

- `student_demo` / `STUDENT`
- `teacher_demo` / `TEACHER`
- `admin_demo` / `ADMIN`

## 后续建议

- 接入真实模型服务（替换 mock 诊断）
- 接入数据库（用户、记录、反馈、统计）
- 增加权限控制与会话过期策略
- 补充自动化测试与接口文档
