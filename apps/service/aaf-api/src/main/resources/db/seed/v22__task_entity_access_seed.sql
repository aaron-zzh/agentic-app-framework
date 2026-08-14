-- ============================================================
-- 任务 Entity Engine 权限、记录规则与菜单
-- ============================================================

-- ---------------- L1 资源读权限 ----------------
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('异步任务读取', 'system:async-task:read', 'system', 'async-task', 'read', 0),
    ('任务执行审计读取', 'system:task-execution:read', 'system', 'task-execution', 'read', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 所有登录角色可访问异步任务资源；L3 规则决定只能看到自己的记录。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p ON p.code = 'system:async-task:read'
WHERE r.code IN ('user', 'guest', 'member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- 执行审计属于平台运维数据，仅管理员可读。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p ON p.code = 'system:task-execution:read'
WHERE r.code IN ('admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- ---------------- L3 异步任务记录范围 ----------------
-- 普通登录用户只读自己的任务；admin 额外得到全量 allow，super_admin 由规则引擎直接放行。
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('async-task', '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}', 'allow'),
    ('async-task', '["admin"]',
     '{"field":"id","op":"gt","value":0}', 'allow')
ON CONFLICT DO NOTHING;

-- ---------------- 管理菜单与角色绑定 ----------------
WITH system_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '系统' AND deleted = FALSE
)
INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT system_group.id, item.title, item.path, item.icon, item.sort_order, 'MENU', TRUE
FROM system_group
CROSS JOIN (
    VALUES
        ('异步任务', '/module/async-task', 'list-restart', 8),
        ('任务执行审计', '/module/task-execution', 'scroll-text', 9)
) AS item(title, path, icon, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM sys_menu menu WHERE menu.path = item.path AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.path IN ('/module/async-task', '/module/task-execution')
WHERE r.code IN ('admin', 'super_admin')
  AND m.deleted = FALSE
ON CONFLICT DO NOTHING;
