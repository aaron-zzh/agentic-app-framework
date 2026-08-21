-- ============================================================
-- Prompt / Skill 智能治理权限、记录范围与菜单
-- ADMIN_MAINTENANCE 仍要求普通动作权限；新增模式权限仅授予 super_admin。
-- ============================================================

-- ---------------- L1 治理访问模式权限 ----------------
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('Prompt 管理维护模式',
     'system:prompt-template:access-mode:admin-maintenance',
     'system', 'prompt-template', 'admin-maintenance', 0),
    ('Skill 管理维护模式',
     'system:skill-definition:access-mode:admin-maintenance',
     'system', 'skill-definition', 'admin-maintenance', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- ADMIN_MAINTENANCE 会同时检查 read/update/create 与模式权限；显式只绑定 super_admin。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.code IN (
    'system:prompt-template:read',
    'system:prompt-template:update',
    'system:prompt-template:access-mode:admin-maintenance',
    'system:skill-definition:read',
    'system:skill-definition:create',
    'system:skill-definition:update',
    'system:skill-definition:access-mode:admin-maintenance'
)
WHERE role.code = 'super_admin'
  AND role.deleted = FALSE
  AND permission.deleted = FALSE
ON CONFLICT DO NOTHING;

-- ---------------- L3 普通访问记录范围 ----------------
-- 治理模式由框架绕过记录/个人范围但保留租户范围；以下规则只约束 DEFAULT。
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
SELECT
    'prompt-template',
    '["*"]',
    '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"visibility","op":"eq","value":"PUBLIC"}]}',
    'allow'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_data_access_rule rule
    WHERE rule.entity_slug = 'prompt-template'
      AND rule.deleted = FALSE
);

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
SELECT
    'skill',
    '["*"]',
    '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"visibility","op":"eq","value":"WORKSPACE"},{"field":"visibility","op":"eq","value":"PUBLIC"}]}',
    'allow'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_data_access_rule rule
    WHERE rule.entity_slug = 'skill'
      AND rule.deleted = FALSE
);

-- ---------------- 管理菜单 ----------------
WITH management_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL
      AND title = '管理'
      AND deleted = FALSE
)
INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT management_group.id, item.title, item.path, item.icon, item.sort_order, 'MENU', TRUE
FROM management_group
CROSS JOIN (
    VALUES
        ('Prompt 治理', '/module/prompt-template', 'message-square-code', 20),
        ('Skill 治理', '/module/skill', 'brain-circuit', 21)
) AS item(title, path, icon, sort_order)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = item.path
      AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu ON menu.path IN ('/module/prompt-template', '/module/skill')
WHERE role.code = 'super_admin'
  AND role.deleted = FALSE
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;
