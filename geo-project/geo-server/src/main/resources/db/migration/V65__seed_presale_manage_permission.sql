-- ============================================================
-- V65__seed_presale_manage_permission.sql
--
-- P1·F·1·b·1 新增:presale.report.manage 权限 key 及 manager 角色绑定。
--
-- 背景:
--   V62 已 seed presale.report.edit_content(edit/derive/freeze/retry 复用此 key)。
--   P1·F·1·b·1 新增 unfreeze/delete 两个 manager 级动作,需要独立权限 key
--   presale.report.manage,仅绑定 manager 角色。
--
-- 版本号选择:
--   V63 / V64 已被仓库占用(V63 为 universal prompt seed,V64 为优化规则)。
--   本迁移选用 V65 避让。
--
-- 仓库 schema 对齐(r4 依据 Codex 针对 V62/V5 的复审反馈):
--   sys_permission(perm_key, perm_name, module, action, status)
--     - created_at / updated_at 有默认值,插入无需指定
--     - status 为 VARCHAR 枚举,有效值 'active' / 'inactive'
--       (对齐 SysPermissionMapper.selectPermKeysByUserId 的 p.status = 'active' 过滤)
--   sys_role(role_key, ...) —— manager 角色 role_key = 'manager'(V5 已 seed)
--   sys_role_permission(role_id, permission_id) —— 通过 JOIN 解析 id 插入
--
-- ============================================================

-- ------------------------------------------------------------
-- Step 1: 权限定义
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_permission (perm_key, perm_name, module, action, status)
VALUES (
    'presale.report.manage',
    'Presale Report Manage',
    'presale',
    'manage',
    'active'
);

-- ------------------------------------------------------------
-- Step 2: 绑定到 manager 角色
-- ------------------------------------------------------------
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_key = 'manager'
  AND p.perm_key = 'presale.report.manage';

-- ============================================================
-- 验证 SQL(执行后人工跑):
--
-- SELECT perm_key, perm_name, module, action, status
-- FROM sys_permission
-- WHERE perm_key = 'presale.report.manage';
--   -- 预期 1 行,status = 'active'
--
-- SELECT r.role_key, p.perm_key
-- FROM sys_role_permission rp
-- JOIN sys_role r ON r.id = rp.role_id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE p.perm_key = 'presale.report.manage';
--   -- 预期仅 1 行,role_key = 'manager'
-- ============================================================
