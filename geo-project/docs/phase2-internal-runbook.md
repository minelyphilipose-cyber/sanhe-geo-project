# 第2步（5/6）内部试跑手册

## 1. 启动前检查
- 数据库迁移状态：
  - `V5` 已修复校验（如历史改动过执行过 `flyway repair`）。
  - `V6`、`V7` 均执行成功。
- Redis 可用（登录刷新链路依赖）。
- 前端使用最新构建（已包含权限路由与按钮控制）。

## 2. 账号准备
- 内部：`super_admin`、`manager`、`delivery_manager`、`operator`、`sales`
- 合伙人：`partner`、`partner_staff`、`partner_viewer`
- 确保合伙人类账号已绑定 `partner_id`。

## 3. 回归验证路径
- 权限路由：
  - 无 `user.manage` 权限账号访问 `/admin/settings/users` 应进入 `/403`。
  - 合伙人账号访问 `/admin/*` 应回到 `/partner/home`。
- 按钮权限：
  - 无 `company.write` 账号看不到“新建客户/编辑客户/新增品牌”。
  - 无 `project.write` 账号看不到“新建项目/编辑项目/保存推进状态”。
  - 无 `partner.write` 账号看不到“新建合伙人/编辑合伙人/余额调整”等按钮。
- 数据隔离：
  - 合伙人账号只看到自身 `partner_id` 数据（客户/项目/合伙人详情）。
- 会话失效：
  - 管理员修改某账号角色或重置密码后，该账号旧 token 再访问应返回 401，并跳回登录页。

## 4. 快速联调脚本
- 执行：
  - `powershell -ExecutionPolicy Bypass -File .\\scripts\\phase2-rbac-smoke.ps1 -AdminUser admin -AdminPass admin123 -PartnerUser <partner_user> -PartnerPass <partner_pass>`
- 脚本会验证：
  - 管理员登录、`/admin/users`、`/partners` 可访问
  - 合伙人登录后可访问自身数据
  - 合伙人访问 `/admin/users` 被拦截

## 5. 问题定位建议
- 启动失败优先看 Flyway：
  - `flyway_schema_history` 是否存在 `success=0`
  - `checksum mismatch` 是否已 repair
- 页面跳登录：
  - 检查 `/api/me` 是否返回 `code=401`
  - 检查 `token_version` 是否与 JWT claim 一致
- 页面空白无菜单：
  - 检查 `/api/me.permissions` 返回是否为空
