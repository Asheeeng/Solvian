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
- 结果展示：`status`、`steps`、`feedback`、`errorIndex`
- AI 识别反馈保存（准确/不准确 + 错误类型 + note）
- 错题本与统计面板（按钮触发抽屉）
- 前端事件上报
- PDF 报告下载接口（占位实现）
- 全链路内存存储实现（便于后续切数据库）

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

## 配置说明

`application.yml` 中已预留：

- PostgreSQL 连接配置（当前阶段未启用持久化）
- 文件上传限制
- CORS 配置
- AI 模型配置（`app.ai.*`）

安全要求：API Key 仅通过环境变量或配置注入，不允许硬编码。

## 后续建议

- 接入真实模型服务（替换 mock 诊断）
- 接入数据库（用户、记录、反馈、统计）
- 增加权限控制与会话过期策略
- 补充自动化测试与接口文档

