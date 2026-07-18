# 模型诊断 Redis 许可与状态恢复基线报告

日期：2026-07-15
阶段：Redis 许可与状态恢复
前置里程碑：数据库与 RBAC、诊断专用执行器均已签收

## 1. 阶段范围

本阶段只实现诊断专用并发许可、并发拒绝落库及超时状态恢复，不新增 Controller、外部 API、前端页面、历史查询、清理服务或数据库迁移。最高迁移版本仍为 V315。

## 2. Redis 原子许可契约

- 全局最多同时进入供应商执行的诊断 Run 为 2；同一操作人最多 1 个。已提交但尚在短暂许可等待中的 RUNNING 幂等占位不代表供应商调用已开始。
- 全局许可和操作人许可由一段 Lua 在一次 Redis 调用中同时检查、占用，避免只取得单边许可。
- Redis Key 固定使用相同 Cluster hash tag：

```text
geo:diagnostic:{model-diagnostic}:permits:global
geo:diagnostic:{model-diagnostic}:permits:operator:{operatorId}
```

- 每个许可使用随机 `ownerToken`；释放脚本只删除匹配的 owner，不会释放其他请求的许可。
- ZSET score 为不可续期的 `deadlineAt`；每次获取成功后按集合最大 score 计算 Key TTL，避免短租约缩短仍有效的长租约，并增加 30 秒回收缓冲。
- Java 在每次 Redis 调用前检查 deadline，并在成功返回后再次检查；若 Redis 调用跨过 deadline 或1秒获取窗口，会按 ownerToken 主动释放刚取得的许可并返回拒绝。Lua 使用 Redis `TIME` 取得执行时刻并原子拒绝 `leaseUntil <= now`。
- 获取窗口最多 1 秒，每 50 毫秒短暂重试，不建立长队列。
- Redis 异常按 fail-closed 处理，不绕过并发限制继续调用供应商。

## 3. 执行与幂等语义

处理顺序冻结为：

```text
按 operatorId + clientRequestId 查询幂等结果
→ 解析当前平台配置
→ 计算一次性 180 秒 deadline
→ 在独立事务中创建并提交 RUNNING 幂等占位
→ 若已存在则直接返回原 Run
→ 获取 Redis 双层许可
→ 按当前 turnNo 重新加载成功的 FREE_CHAT 历史
→ 条件固化最终 request_messages_json
→ 执行一次供应商调用
→ 条件写入终态
→ ownerToken 释放许可
```

- 合法请求在 1 秒内未取得许可时，将自己已创建的 `RUNNING` 条件更新为终态 `REJECTED`，错误码为 `DIAGNOSTIC_BUSY`，不调用供应商。
- Redis 不可用时同样将该占位条件更新为 `REJECTED`，错误码为 `DIAGNOSTIC_PERMIT_UNAVAILABLE`；线程中断使用 `DIAGNOSTIC_PERMIT_INTERRUPTED`。
- 业务许可申请发生在 RUNNING 幂等占位提交之后；同一请求并发重放返回原 `RUNNING`，不会因原请求持有的操作人许可而抢先写入 `REJECTED`。
- `RUNNING → REJECTED` 只用于尚未调用供应商的本地许可拒绝，并同时受 `status = 'RUNNING' AND deadline_at > NOW(3)` 条件保护；已超时记录转为 `ABANDONED`。
- RUNNING 占位创建时 `request_messages_json` 暂存为空数组，不提前冻结自由对话上下文；取得操作人许可后，以 `turn_no < 当前轮次` 查询最近9个成功 FREE_CHAT 轮次，追加当前用户消息并条件写回最终快照。上一轮在许可等待期间完成时会进入本轮供应商上下文，未来轮次、探针和失败轮次不会进入。
- `REJECTED` 消耗审计 `turnNo`，但不进入后续自由对话上下文。
- 180秒 deadline 覆盖幂等占位、许可等待、供应商调用和最终持久化；Redis业务许可覆盖供应商调用及最终持久化，所有已取得许可的返回路径均尝试释放，释放失败由 TTL 回收。

## 4. 状态恢复

- 应用启动后最多扫描 10 个批次，每批 100 条过期 `RUNNING`。
- 定时任务默认每 30 秒扫描一个批次，可通过 `geo.model-diagnostic.recovery.*` 配置关闭或调整间隔。
- 扫描条件固定为 `status = 'RUNNING' AND deadline_at <= NOW(3)`。
- 状态更新继续复用条件更新 `WHERE id = ? AND status = 'RUNNING' AND deadline_at <= NOW(3)`，多实例同时扫描时只有一个实例能赢得更新。
- 恢复事务使用 `REQUIRES_NEW`；单次任务失败只记录错误，不阻断应用启动或后续调度。

## 5. 自动化门禁

### 5.1 诊断专项

```text
Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

覆盖许可服务、Lua 调用契约、成功返回跨 deadline 后主动释放、并发拒绝、Redis 异常拒绝、幂等优先、许可释放、许可后上下文重建、状态恢复和既有执行器契约。

### 5.2 真实 Redis 强制门禁

执行：

```text
MODEL_DIAGNOSTIC_REDIS_IT_HOST=...
MODEL_DIAGNOSTIC_REDIS_IT_PORT=...
MODEL_DIAGNOSTIC_REDIS_IT_PASSWORD=...
mvn -Predis-it test
```

结果：

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

真实执行 Lua 并验证：全局 2/操作人 1 的原子限制、错误 owner 不可释放、正确 owner 可释放、过期租约无需进程主动释放即可回收、短租约不会缩短全局长租约 TTL、已过 deadline 的申请不会写入许可。测试使用随机 hash tag 键并在结束时清理。

不提供 Redis 且 Docker 不可用时，强制门禁结果为：

```text
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
BUILD FAILURE
```

普通本地模式允许明确跳过，结果为 5 项跳过，不会被描述为通过；验收和后续许可变更必须执行 `redis-it` 强制门禁。

### 5.3 MySQL 与生产兼容回归

```text
MySQL 8 / Flyway V315 / Mapper：11 通过，0 失败
Codec / 生产适配 / 审计 / 传输：18 通过，0 失败
BUILD SUCCESS
```

MySQL 回归包含真实过期扫描、条件废弃更新、RUNNING 条件拒绝、`turn_no < 当前轮次`上下文边界和最终消息快照条件写入；同时使用 latch 和两个真实事务证明并发重放会在会话锁后读取原 RUNNING 占位。受控并发测试证明上一轮在当前轮许可等待期间完成后，其 user/assistant 对会进入当前供应商请求。生产联网调用路由、Codec 和审计语义未改变。

## 6. 发布门禁与边界

- 本阶段未创建 V316，未修改 V315。
- 本阶段未增加 Controller、Redis 长队列、许可续租、SSE、前端或历史清理能力。
- 服务端全量回归仍需在开发库 V315 checksum 经合规处理后执行；不得运行 Flyway repair 掩盖工作区迁移差异。
- 后续 Controller 和探针解析层仍必须分别传递 `clientUserMessage` 与 `resolvedUserMessage`。

上述实现和门禁通过后，才可进入“历史与清理”或前端诊断台阶段。
