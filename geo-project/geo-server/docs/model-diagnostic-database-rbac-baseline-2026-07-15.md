# 模型能力诊断台：数据库与 RBAC 基线

日期：2026-07-15
阶段：数据库与 RBAC
迁移版本：V315（不可变基线）+ V316（契约补全）

## 迁移不可变性修订（2026-07-15）

开发库已于2026-07-15 12:09:35执行初始V315，Flyway保存的CRC32校验值为`459344448`。后续评审修复曾直接编辑V315，导致本地脚本校验值变为`142227398`，应用启动时被Flyway正确拦截。

现按前向迁移原则修复：

- V315恢复为数据库实际执行过的初始内容，校验值重新固定为`459344448`；
- V316增加`template_version`、会话完整身份组合约束、会话操作人外键及更严格的状态/结论检查约束；
- 最终数据语义保持本报告冻结的契约不变，只调整这些契约所在的迁移版本；
- 未执行`flyway repair`，未手工修改`flyway_schema_history`；
- MySQL 8强制门禁从baseline 314依次执行V315、V316，13项全部通过，当前版本为316；
- 开发库通过应用正常启动流程执行V316，健康检查为`UP`。

下文原始阶段验收数据作为历史记录保留；其中“全部位于V315”“只执行1个迁移”“当前版本315”的表述，均由本节修订为V315不可变基线加V316前向补全。

## 交付范围

本阶段只建立诊断数据语义和权限基线，不包含供应商调用、Redis许可、状态扫描器、历史清理接口或前端页面。

## 数据结构

### ai_model_diagnostic_sessions

- 以 `(operator_id, session_id)` 绑定会话归属；
- 以 `(id, operator_id, session_id)` 组合外键把运行记录的完整冗余身份绑定到会话；
- `operator_id` 通过 `ON DELETE RESTRICT` 关联操作人；
- `next_turn_no` 由服务端在行锁下分配；
- 失败、拒绝和废弃运行同样消费审计 turn；
- 会话读取和追加必须使用操作人范围查询。

### ai_model_diagnostic_runs

关键约束：

- `(operator_id, client_request_id)` 唯一，支持操作人级接口幂等；
- 保存 SHA-256 `request_fingerprint`，同一幂等ID参数不一致时由业务层返回409；
- `(operator_id, session_id, turn_no)` 唯一；
- 保存非密钥配置快照、配置哈希、实际探针版本和生产模板版本；
- 保存服务端构建的完整上下文、当前用户消息和回答；
- 保存完整诊断结论、来源、引用、usage、标准Token计数和脱敏报文；
- `deadline_at` 在创建时固化；
- `(status, deadline_at)` 支持ABANDONED扫描；
- `(created_at, id)` 支持30天分批清理。

数据库检查约束明确区分执行状态与诊断结论：

```text
SUCCEEDED -> PASS/WARNING/FAIL
FAILED    -> FAIL
RUNNING/REJECTED/ABANDONED -> NULL
```

`ModelDiagnosticRunStatus`只允许：

```text
RUNNING -> SUCCEEDED/FAILED/REJECTED/ABANDONED
```

`RUNNING -> REJECTED`是并发竞态评审后增加的唯一受控补充：先提交RUNNING幂等占位，只有尚未调用供应商且业务许可获取失败时，才通过`status='RUNNING' AND deadline_at>NOW(3)`条件更新为REJECTED。该补充不改变V315结构和`REJECTED -> conclusion NULL`约束，目的是确保同一幂等请求并发重放始终读取原Run，而不会抢先固化为拒绝结果。

## RBAC

新增单一权限：

```text
ai.platform.diagnose
```

V315默认授予：

- `manager`
- `super_admin`

未授予operator、delivery_manager、sales及合作方角色。后续菜单、路由和接口统一使用同一权限常量。

## Deadline竞态基线

`AiModelDiagnosticRunMapper.markAbandonedIfExpired`使用条件更新：

```sql
WHERE id = ?
  AND status = 'RUNNING'
  AND deadline_at <= ?
```

并递增状态版本。正常终态的完整条件更新、影响行数为0后的重新读取和主动ABANDONED处理将在诊断专用执行器阶段实现。

## 验证结果

### P1 闭环

- `template_version` 由 V316 加入，实体与契约测试同步完成，和 `probe_version` 分别保存；
- 会话表已建立 `(id, operator_id, session_id)` 唯一键，运行表通过同字段组合外键绑定完整会话身份；
- 会话 `operator_id` 已通过 `ON DELETE RESTRICT` 绑定 `sys_user(id)`；
- 新增真实 MySQL 8 集成测试，执行 Flyway `V314 -> V315 -> V316`，并通过真实 MyBatis Mapper验证约束和条件更新。

静态持久化/RBAC契约：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
```

MySQL 8/Flyway/Mapper 集成：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
MySQL: 8.0
Flyway: baseline 314, migrations executed 2, current version 316
BUILD SUCCESS
```

### 强制门禁

普通本地测试在没有 Docker 或外部 MySQL 时允许明确跳过真实集成测试：

```text
命令：mvn "-Dtest=ModelDiagnosticPersistenceContractTest,ModelDiagnosticMysqlIntegrationTest" test
Tests run: 11, Failures: 0, Errors: 0, Skipped: 5
BUILD SUCCESS
```

该结果只代表6项静态契约通过，不得表述为11项通过，也不能用于数据库阶段验收。

数据库阶段验收及后续诊断迁移变更必须执行：

```text
mvn -Pmysql-it test
```

`mysql-it` Profile只执行 `ModelDiagnosticMysqlIntegrationTest`，并设置强制门禁属性。缺少 Docker 且未配置外部 MySQL 时，测试抛出初始化错误并返回 `BUILD FAILURE`；不得通过 assumption 跳过。

本机负向门禁验证：

```text
Tests run: 1, Failures: 0, Errors: 1, Skipped: 0
Required mysql-it gate cannot run
BUILD FAILURE
```

提供 MySQL 8 后的强制门禁验证：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
MySQL: 8.0
Flyway: baseline 314, migrations executed 2, current version 316
BUILD SUCCESS
```

集成测试优先使用 Testcontainers `mysql:8.0.36`；也可配置 `MODEL_DIAGNOSTIC_MYSQL_IT_HOST`、`PORT`、`USER`、`PASSWORD` 连接 MySQL 8。外部模式自动创建随机临时库并在测试后删除，不迁移或清理共享业务库。
