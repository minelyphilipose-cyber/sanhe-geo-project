# 第2步（小步1+2）实施进度

## 小步1：RBAC基础模型落库

进入本小步前累计已完成：
- 认证/JWT/登录基础已存在。
- Partner/Customer/Project 基础 CRUD 已可用。
- 合伙人读隔离（按 partner_id）在业务层已生效。

本小步新增完成：
- 新增 RBAC 核心表：
  - `sys_role`
  - `sys_permission`
  - `sys_user_role`
  - `sys_role_permission`
- 新增迁移脚本：`V5__phase2_rbac_user.sql`。
- 预置角色（含 `partner/partner_staff/partner_viewer`）与最小权限点。
- 将历史 `sys_user.role` 自动回填到 `sys_user_role`（兼容存量账号）。

本小步未完成/遗留：
- 尚未落地“接口级权限注解/AOP 统一鉴权”（属于小步3）。
- 尚未将权限点下发给前端做按钮级控制（属于小步5）。

下一小步输入前提（依赖）：
- Flyway 需先成功执行到 V5。
- 角色基础数据存在且状态为 `active`。

风险与回滚点：
- 风险：历史库若存在失败迁移记录，会阻塞 V5 执行。
- 回滚点：可仅回滚 V5 新表；不影响既有业务主表。

## 小步2：用户管理接口 + 合伙人账号模型

进入本小步前累计已完成：
- 小步1 RBAC 表结构与角色种子数据已就绪。
- `sys_user.role` 仍保留，登录逻辑兼容旧链路。

本小步新增完成：
- 新增用户管理后端接口（`/api/admin`）：
  - `GET /users`（分页筛选）
  - `GET /users/{id}`（详情）
  - `POST /users`（创建）
  - `PUT /users/{id}`（编辑）
  - `PUT /users/{id}/status`（启停用）
  - `POST /users/{id}/reset-password`（重置密码）
  - `PUT /users/{id}/role`（分配角色）
  - `GET /roles`（角色选项）
- 新增账号模型约束：
  - `partner/partner_staff/partner_viewer` 必须绑定 `partnerId`
  - 内部角色禁止绑定 `partnerId`
- 新增管理权限门槛（当前阶段）：
  - 仅 `super_admin/manager` 可调用用户管理接口。
- 角色分配实现“单角色主映射”：
  - 同步更新 `sys_user.role`（兼容 JWT 现有角色字段）
  - 同步维护 `sys_user_role` 关联。

本小步未完成/遗留：
- 前端“用户管理”页面仍未切到真实接口（属于小步5）。
- 角色变更后的已登录 Token 不会即时失效（需后续补会话失效策略）。

下一小步输入前提（依赖）：
- 小步3 需要基于当前 RBAC 表与用户接口继续实现统一鉴权。
- 建议先准备 5 类验收账号：`super_admin/manager/partner/partner_staff/partner_viewer`。

风险与回滚点：
- 风险：若错误把内部账号绑了 partnerId，会被接口校验拦截。
- 风险：生产环境执行角色切换时，旧 token 仍可能短期持有旧权限。
- 回滚点：可暂停使用 `/api/admin/users/{id}/role`，仅保留历史 `sys_user.role`。

