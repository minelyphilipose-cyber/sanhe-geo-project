# 自媒体项目排期异步提交

创建及补充排期原来在 HTTP 请求中计算时间、逐条写入计划和预占额度，计划数量增多时会超过前端 30 秒请求超时。项目页面现改用后台任务提交，继续执行 `ProjectSelfMediaScheduleService.createForProject`，适用于所有自媒体平台。

## 操作和状态

- 点击创建/补充排期，完成原有预检与确认后，接口只保存任务并返回任务 ID。
- 页面显示“后台排期中”，每 3 秒查询结果，处理中禁止重复提交。离开或刷新页面不会取消任务，重新打开时查询该项目、月份的最新任务。
- 完成后展示本次新增计划和结转数量，重新加载批次及剩余需求；失败显示原因，保留已有排期，可以重新提交。
- `pending` 同时表示等待执行和正在执行，`success` 表示计划创建完成，`failed` 表示本次排期失败。这些状态与文章生成、发布、审核状态分别存储。

## 执行保证

- 新入口：`POST /api/projects/{id}/self-media-schedules/auto-create-async`；请求体与原同步入口相同，支持 `Idempotency-Key`。
- 状态入口：`GET /api/projects/{id}/self-media-planning-tasks/{targetMonth}/latest`。原同步入口保留兼容，项目页面使用异步入口。
- 同一项目、月份最多一个未完成任务；同一请求标识重试返回原任务，参数改变时拒绝复用该标识。
- 后台重新核对提交人员的有效性、内部人员范围、品牌权限及原有结转权限，线程结束后恢复安全上下文。
- 数据库 `FOR UPDATE SKIP LOCKED` 锁定待处理任务，多实例避免重复执行。任务结果、计划和项目计划额度预占在同一事务中提交；失败回滚后另行记录失败原因。服务中断时未提交任务保持待处理，重启后再次领取。
- 项目计划预占在已有计划事务中参与该事务，不再独立提交。无外层事务的原有调用仍保留独立额度事务及补偿；已发布任务的额度确认和普通分发额度事务未修改。
- 每个后端实例使用一个专用后台线程，定时线程只派发任务。额度不足、平台能力、账号状态、结转、既有排期保护继续由原服务校验。

## 生效步骤和验证

重新构建、启动后端及前端，后端通过 Flyway 应用 `V375__project_self_media_async_planning.sql`，创建任务表。仅更新前端而没有更新后端会缺少新接口；本次无需更新浏览器扩展或本地助手。

本地通过 312 项后台回归、14 项前端测试、Vue 类型检查及前端生产构建。包含真实 H2 事务回滚、数据库重复请求约束、多个执行线程跳过已锁任务、操作者权限、刷新恢复、月份切换、断网重试和页面退出。H2 使用 MySQL 模式并移除迁移中的存储引擎和 STORED 声明；尚未在生产 MySQL 执行迁移或提交真实排期。

证据在仓库 `run-logs`：`self-media-async-planning-backend-regression.log`、`self-media-async-planning-frontend-tests.log`、`self-media-async-planning-typecheck.log`、`self-media-async-planning-build.log`。`self-media-async-plan-quota-before.log` 复现了三个平台计划回滚后额度未释放；`self-media-async-plan-quota-after.log` 验证修复。
