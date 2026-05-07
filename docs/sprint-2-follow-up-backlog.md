# Sprint 2 Follow-up Backlog

## Sprint 3 W1

### S2-LESSON-1: 全 entity 关键字审计

背景：B6b 联调时发现 `audit_log.sensitive` 是 MySQL 8 保留字，Sprint 1 起真实 INSERT 都会触发 SQL 语法错误；此前 `mvn test` 全绿，是因为 audit 相关测试 100% 使用 mock，没有真实写入 `audit_log`。

范围：用 `information_schema.keywords` 全表扫描所有 entity 的 `@TableName` 和字段名，输出撞 MySQL 8 reserved words 清单。

修复：每个撞库的字段加 `@TableField` 反引号转义。

优先级：Sprint 3 W1 第一个 PR。

### S2-LESSON-2: 评估 testcontainers 替代真实 MySQL

背景：B6b 引入的 `AbstractAuditDbIntegrationTest` 依赖真实 MySQL 8，本地跑 `mvn test` 必须先起 MySQL。

评估：确认 `testcontainers` + MySQL 8 image 在 CI 时间和本地体验上是否值得。如果值得，迁移所有 DB 集成测试。

优先级：Sprint 3 backlog，不阻塞，按体验决定。
