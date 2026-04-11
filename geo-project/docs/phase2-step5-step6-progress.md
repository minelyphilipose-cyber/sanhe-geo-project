# 第2步（小步5+6）实施进度

## 进入5、6小步前累计已完成：
- 小步1：RBAC 基础表与权限点落库（V5）。
- 小步2：用户管理后端接口与合伙人账号模型（主账号/员工/只读）。
- 小步3：后端接口权限统一改造（`ensurePermission`）。
- 小步4：数据权限隔离统一改造（列表/详情均按 partner scope 收敛）。

## 5、6小步新增完成：
- 第5步（前端权限打通）
  - 新增前端权限模型：`UserInfo.permissions`、`RouteMeta.permissions`。
  - 登录后和路由守卫中接入 `/api/me` 权限同步。
  - 路由新增 `/403` 无权限页，路由守卫支持 `roles + permissions` 双校验。
  - 侧边菜单改为按权限点过滤展示。
  - 关键写操作按钮改为按权限点渲染：
    - 合伙人页：新建/编辑/状态变更/余额调整（`partner.write`）
    - 客户页：新建/编辑（`company.write`）
    - 客户详情：编辑客户/新增品牌/编辑品牌（`company.write`），基于品牌建项目（`project.write`）
    - 项目页：新建/编辑/推进状态（`project.write`）
  - 用户管理页从占位改为可用页面：
    - 账号列表、创建、编辑、状态切换、重置密码、角色分配。
- 第6步（联调与验收交付）
  - 新增联调脚本：`scripts/phase2-rbac-smoke.ps1`。
  - 新增本进度文档，作为下一轮可直接沿用的“累计完成模板”。

## 5、6小步未完成/遗留：
- 尚未补齐自动化单元测试/集成测试（当前为联调脚本 + 手工验收清单）。
- 前端仍有少量历史页面文案乱码（不影响权限链路，可后续统一清理）。
- 用户管理页暂未做细粒度字段禁改（例如防止修改超级管理员自身）。

## 下一步输入前提（依赖）：
- 数据库需执行到 `V7__phase2_token_version.sql`（含 token_version 字段）。
- 如数据库已执行过旧 V5，需先 `flyway repair`，再启动让 `V6/V7` 自动执行。
- 准备验收账号：`super_admin / manager / partner / partner_staff / partner_viewer`。
- 执行 `scripts/phase2-rbac-smoke.ps1` 进行首轮联调。
