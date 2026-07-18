# V315 Flyway校验失败恢复报告

日期：2026-07-15
结论：已修复，应用可正常启动

## 1. 故障现象与根因

应用启动时Flyway校验失败：

```text
Migration checksum mismatch for migration version 315
Applied to database : 459344448
Resolved locally    : 142227398
```

开发库已执行初始V315后，后续评审修复继续直接修改了同一迁移文件。Flyway因此拒绝在数据库结构与本地迁移历史不一致的状态下启动。该异常发生在ApplicationContext初始化阶段，与业务Mapper或供应商调用无关。

只读核对开发库实际结构后确认，数据库中的V315为初始版本，尚不包含后来加入的`template_version`、会话完整身份组合外键和收紧后的状态/结论检查约束。

## 2. 修复方式

- 将`V315__ai_model_diagnostic_foundation.sql`恢复为开发库实际执行的初始内容；
- 固定恢复后V315的Flyway CRC32校验值为`459344448`；
- 新增`V316__complete_ai_model_diagnostic_contract.sql`，以前向迁移补齐后续评审通过的数据契约；
- 静态契约测试同时读取V315与V316，并增加V315不可变校验；
- MySQL集成测试目标版本更新为316，并验证两条迁移按顺序执行。

本次没有执行`flyway repair`，没有手工修改或删除`flyway_schema_history`记录，也没有重建开发数据库。

## 3. V316承接内容

- 会话表增加`(id, operator_id, session_id)`唯一约束；
- 会话操作人通过`ON DELETE RESTRICT`关联`sys_user(id)`；
- 运行表增加可空`template_version`；
- 运行记录通过`(session_record_id, operator_id, session_id)`组合外键绑定完整会话身份；
- 状态与结论检查约束显式要求`SUCCEEDED/FAILED`的`conclusion`非空。

## 4. 验证证据

诊断模块普通测试：

```text
Tests run: 103, Failures: 0, Errors: 0, Skipped: 19
BUILD SUCCESS
```

MySQL 8强制门禁：

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
Flyway: baseline 314, migrations executed 2, current version 316
BUILD SUCCESS
```

应用以`dev`配置正常启动，Flyway保留V315原校验值并顺序执行V316；Actuator健康检查返回`UP`，数据库与Redis均为`UP`。验证完成后已停止本次临时启动的应用进程。

开发库当前迁移记录：

```text
315  checksum=459344448   success=1
316  checksum=1586740349  success=1
```

## 5. 后续约束

V315和V316一经进入任何共享环境都不得再原地编辑。后续数据库变更必须创建新的顺序迁移；CI与发布继续执行`mysql-it`强制门禁，禁止以`repair`掩盖迁移内容漂移。
