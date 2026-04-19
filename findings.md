# 发现与决策

## 需求
- 用户要求找到 `Copilot` 文件夹并读取其中项目文件。
- 需要输出：需求分析、架构图、E-R 图、用例图、时序图、数据库设计图。
- 所有图必须使用 draw.io 完成。
- 项目被视为完整项目，需要完整分析，不能只做局部。
- 不允许修改现有项目文件。
- draw.io 文件需放在项目内新目录，并额外导出 PDF。
- 交付风格采用混合风格：兼顾答辩展示与工程表达。
- 需求分析部分以文字文档交付，其余图用 draw.io。
- 图纸组织方式采用拆分交付：每张图一个 `.drawio`，并导出对应 PDF。
## 研究发现
- 项目根目录位于 `/Users/suis/Developer/Codex/Copilot`。
- 项目名在 README 中标识为 `Solvian`。
- 技术栈：Java 17、Spring Boot 3.2.5、Maven、静态前端、PostgreSQL/MyBatis-Plus 预留。
- 核心业务覆盖：注册/登录、教师工作台、题目上传、AI 诊断、反馈回收、错题本/统计、事件上报、报告下载、模型连通性测试。
- README 已声明 PostgreSQL 持久化覆盖认证、会话、诊断记录、AI 反馈、事件日志。
- 当前已发现数据库表：`app_user`、`app_session`、`diagnosis_record`、`diagnosis_task`、`ai_feedback_record`、`event_record`。
- 控制器层暴露接口：`/api/auth/*`、`/api/diagnose`、`/api/history`、`/api/dashboard-summary`、`/api/ai-feedback`、`/api/report/{recordId}/pdf`、`/api/model/test`。
- 鉴权通过 `X-Auth-Token` 头与 `AuthInterceptor` 传递 `SessionInfo` 到各业务控制器。
- 前端 `src/main/resources/static/js/common/storage.js` 统一封装认证、异步诊断、历史、统计、反馈、PDF 下载请求。
- 诊断主链路已经从同步 `evaluate` 扩展到异步 `diagnose + 轮询 taskId` 模式。
- 用户刚确认图纸风格采用“混合风格”：既适合答辩展示，也保留工程表达。

## 技术决策
| 决策 | 理由 |
|------|------|
| 先从 README + SQL 建立业务全景 | 这是需求分析、数据库设计与架构图的最高信息密度来源 |
| 后续继续读 Java Controller/Service/前端页面 | 需要补全用例、时序与组件关系 |
| draw.io 文件单独新建 | 满足“不修改现有文件”限制 |
| 交付目录放在项目内 | 用户已确认选择项目内新目录 |
| 交付格式包含 `.drawio` 与 PDF | 用户要求顺便导出 PDF |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| Read 工具对普通文件误传 pages 参数 | 后续普通文本读取时不再传 pages |
| Agent 工具尝试 worktree 隔离失败 | 回退为主会话内直接阅读源码与脚本 |

## 资源
- `/Users/suis/Developer/Codex/Copilot/README.md`
- `/Users/suis/Developer/Codex/Copilot/src/main/resources/schema-postgresql.sql`
- `/Users/suis/Developer/Codex/Copilot/src/main/resources/data-postgresql.sql`

## 视觉/浏览器发现
- 当前尚未使用浏览器工具。
- 后续图纸内容本身属于强视觉交付物，需在设计阶段保持图例、分层与关系命名一致。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*