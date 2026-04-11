# 第2步（小步3+4）实施进度

## 小步3：后端权限校验框架化

进入3、4小步前累计已完成：
- 已完成小步1：RBAC 基础表与角色/权限种子数据（V5）已落库。
- 已完成小步2：用户管理接口（创建/编辑/启停/重置密码/分配角色）可用。
- `sys_user.role` 与 `sys_user_role` 已做兼容同步。

3、4小步新增完成：
- 新增权限查询能力：
  - `SysPermissionMapper.selectPermKeysByUserId(...)`
  - `PermissionService`（DB 权限 + 兼容回退策略）
- `CurrentUserService` 增强：
  - `ensurePermission(permKey)` 统一权限校验入口
  - 保留兼容方法，但业务主路径改为权限点控制
- 业务模块已切换到权限点控制：
  - Partner：`partner.read / partner.write`
  - Company/Brand：`company.read / company.write`
  - Project：`project.read / project.write`
  - UserAdmin：`user.manage`
- `/api/me` 返回扩展为“当前用户基础信息 + 权限列表”，为后续前端路由/按钮控制提供数据来源。

## 小步4：数据权限隔离升级

3、4小步新增完成：
- `CurrentUserService` 新增统一数据范围方法：
  - `resolvePartnerQueryScope(...)`：统一列表查询 partner_id 范围
  - `ensurePartnerResourceAccess(...)`：统一详情越权检查
- Company/Project 列表与详情改为统一数据范围逻辑。
- Brand 列表在指定 `companyId` 时先校验公司归属，避免跨合伙人探测。
- Partner 列表对合伙人账号自动收敛为“仅自己”。

3、4小步未完成/遗留：
- 前端菜单/按钮级权限控制尚未对接（第5小步处理）。
- 尚未实现“角色变更后已签发 token 的即时失效”（可在后续加版本号或黑名单策略）。
- 未做自动化测试用例补齐（当前为代码实现层完成）。

下一步输入前提（依赖）：
- 数据库已执行到 `V5__phase2_rbac_user.sql`。
- 需准备多角色验收账号：`super_admin / manager / partner / partner_staff / partner_viewer`。
- 第5小步可直接基于 `/api/me` 的 `permissions` 做路由与按钮权限联动。

风险与回滚点：
- 风险：若线上历史库存在权限关联缺失，可能触发 403（当前已有 legacy 回退策略降低风险）。
- 风险：迁移执行前若有 Flyway 失败记录，仍会阻塞启动。
- 回滚点：
  - 可先回退服务层对 `ensurePermission` 的调用，临时恢复旧角色判断；
  - 或仅保留 V5 表结构，暂停启用新权限关联数据。

