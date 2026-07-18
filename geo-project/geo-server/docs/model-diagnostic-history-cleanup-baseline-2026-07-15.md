# 大模型诊断台：历史与清理阶段基线（2026-07-15）

## 1. 阶段结论

本阶段实现诊断历史查询服务、终态操作审计、30天自动清理和清理任务分布式互斥。

本阶段未增加外部 Controller、前端页面、手工删除入口或新的数据库迁移；V315未修改，未创建V316。历史服务将在后续后端API/前端联调阶段由Controller映射为固定响应DTO，不能直接扩大为不受控的实体查询。

## 2. 历史查询契约

### 2.1 归属与权限

- 所有列表、详情和会话恢复入口先校验 `ai.platform.diagnose`。
- 数据查询必须同时使用当前登录人的 `operator_id`；不得先按主键查询后在Java层补做归属判断。
- `sessionId` 必须解析并规范化为UUID；非法UUID直接返回400，不访问数据库。
- 不存在或不属于当前操作人的 Run/Session 统一按404处理，避免泄露其他操作人的记录是否存在。

### 2.2 列表投影

- 列表支持平台配置、请求模型、基础/联网模式、执行状态、诊断结论和创建时间范围过滤。
- 页码最小为1，默认每页20，最大每页100。
- 排序固定为 `created_at DESC, id DESC`。
- 列表SQL使用显式轻投影，不读取回答、消息、搜索证据、来源、引用、usage、配置快照或脱敏协议报文。

### 2.3 详情与会话恢复

- 详情和会话恢复允许读取诊断展示所需的用户消息、回答、来源、引用、usage及脱敏请求/响应。
- SQL使用显式白名单列，不装载 `request_fingerprint`、`config_snapshot_json`、`config_snapshot_hash` 和 `endpoint_url`。
- 会话恢复按 `turn_no ASC, id ASC` 返回审计轮次；失败、拒绝和废弃记录可展示，但后续模型上下文仍只由执行阶段既有的成功FREE_CHAT查询生成。

## 3. 操作审计

终态事件固定为：

```text
action      = AI_MODEL_DIAGNOSTIC_RUN
targetType  = ai_model_diagnostic_run
targetId    = run.id
userId      = run.operator_id
```

审计 `extra` 仅保存：

```text
platformConfigId
diagnosticMode
testMode
status
conclusion
durationMs
errorCategory
errorCode
```

不得写入用户消息、系统提示词、回答、配置快照、密钥引用、脱敏请求或脱敏响应。正常执行产生的新终态记录一次；幂等重放不重复记录。恢复扫描只有在赢得 `RUNNING → ABANDONED` 条件更新时才记录。

持久化层的完成、失败和执行前拒绝统一返回：

```text
run
transitionedByCaller
```

准备消息结果同样携带 `transitionedByCaller`。只有当前事务的终态条件更新实际影响1行时该值才为true，Coordinator才允许写操作审计；若恢复任务已经先写入ABANDONED，后续线程只返回现有Run且不得重复审计。

## 4. 30天保留与自动清理

### 4.1 删除边界

- 默认保留30天，可通过 `geo.model-diagnostic.cleanup.retention-days` 或 `MODEL_DIAGNOSTIC_RETENTION_DAYS` 调整。
- 删除谓词固定为 `created_at < cutoff`，等于cutoff的边界记录保留。
- 每个事务最多删除1,000条；定时任务默认每批500条、每次最多20批。
- 每批使用 `REQUIRES_NEW`，先删除Run，再删除已经没有Run引用的过期空Session。
- Session过期时间使用 `COALESCE(last_run_at, created_at)`；仍有任意Run引用时不得删除。
- 第一期没有手工删除接口或页面按钮。

### 4.2 调度与互斥

- 默认每天 `03:30`（`Asia/Shanghai`）执行，可通过 `geo.model-diagnostic.cleanup.cron/zone` 调整。
- 多实例共享 Redis Key `geo:model-diagnostic:cleanup:lock`，使用 `SET NX` 和30分钟TTL。
- 释放使用Lua比较owner token后删除；错误owner不能释放其他实例的锁。
- Redis不可用或锁已被占用时fail-closed，本实例跳过本轮数据库清理。
- 即使任务异常也会尝试owner-token释放；锁提前过期后不会误删后来实例取得的新锁。

## 5. 自动化证据

### 5.1 完整诊断专项（包含真实MySQL和Redis）

使用MySQL 8随机临时库和Redis随机隔离Key执行：

```text
Tests run: 87, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

MySQL临时库从V314 baseline迁移到V315，完成后自动删除；未连接、迁移或清理开发业务库，未执行Flyway repair。

竞态回归先由恢复服务赢得 `RUNNING → ABANDONED` 并写入一次审计，再分别让完成、拒绝和准备消息路径返回同一终态，三种情况下最终审计次数均保持为1。

### 5.2 强制MySQL门禁

```text
mvn -Pmysql-it test
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

新增证据覆盖动态历史SQL、操作人归属、轻投影映射、严格cutoff、批量Run删除、空Session清理，以及边界/近期记录保护。

### 5.3 强制Redis门禁

```text
mvn -Predis-it test
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

新增证据覆盖cleanup锁的NX、TTL和owner-token释放；所有测试Key使用随机前缀并在结束时清理。

显式移除外部测试连接且Docker不可用时，两条强制门禁均为：

```text
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
BUILD FAILURE
```

因此真实MySQL/Redis验证不能静默降级为0执行或全部跳过。

### 5.4 生产联网兼容回归

```text
Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

生产联网路由、Codec、Fixture、审计、结果投影、传输和清理语义未发生变化。

## 6. 后续交接边界

- 后端API阶段应仅把本阶段历史服务映射为固定DTO，不得退回 `BaseMapper.selectById` 或 `SELECT *`。
- 前端历史列表不得依赖回答、原始报文等重字段；详情按需加载。
- 详情中的来源URL仍需在后续接口/前端层执行HTTP/HTTPS白名单和安全外链处理。
- 服务端全量回归继续保留既定发布门禁：开发库V315 checksum按团队流程合规处理后再执行，不得用Flyway repair掩盖迁移差异。
