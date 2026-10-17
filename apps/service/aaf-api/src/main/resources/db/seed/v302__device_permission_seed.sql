-- ============================================================
-- 通用设备层 module/device 权限码与菜单
-- 场景角色（如养老运营）的授权由对应场景包的种子追加。
-- ============================================================

-- ==================== 设备权限与菜单 ====================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('设备读取', 'device:device:read', 'device', 'device', 'read', 0),
    ('设备绑定', 'device:device:create', 'device', 'device', 'create', 0),
    ('设备更新', 'device:device:update', 'device', 'device', 'update', 0),
    ('设备导出', 'device:device:export', 'device', 'device', 'export', 0),
    ('设备引用', 'device:device:reference', 'device', 'device', 'reference', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'device:device:read',
        'device:device:create',
        'device:device:update',
        'device:device:export',
        'device:device:reference'
    )
WHERE role.code IN ('org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- 设备通用层独立「设备管理」菜单组，不挂在任何场景（如养老）之下。
INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT NULL, '设备管理', NULL, 'bot', 35, 'GROUP', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '设备管理' AND deleted = FALSE
);

WITH device_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '设备管理' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT device_group.id,
       '设备',
       '/module/device',
       'bot',
       1,
       'MENU',
       TRUE,
       'device:device:read'
FROM device_group
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = '/module/device' AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path = '/module/device'
       OR (menu.parent_id IS NULL AND menu.title = '设备管理')
WHERE role.code IN ('org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;


-- 管理员按组织范围访问设备；场景角色的数据范围由对应场景包的种子追加。
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('device', '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;
