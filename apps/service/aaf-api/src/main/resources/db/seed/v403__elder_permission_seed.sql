-- ============================================================
-- AAF-120 养老业务权限、访问规则与菜单种子
-- 本文件由各子任务按清晰分段追加；禁止重排其它任务分段。
-- ============================================================

-- ==================== #12004 profile：L1 权限码 ====================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('长者读取', 'elder:profile-elder:read', 'elder', 'profile-elder', 'read', 0),
    ('长者创建', 'elder:profile-elder:create', 'elder', 'profile-elder', 'create', 0),
    ('长者更新', 'elder:profile-elder:update', 'elder', 'profile-elder', 'update', 0),
    ('长者删除', 'elder:profile-elder:delete', 'elder', 'profile-elder', 'delete', 0),
    ('长者导出', 'elder:profile-elder:export', 'elder', 'profile-elder', 'export', 0),
    ('长者引用', 'elder:profile-elder:reference', 'elder', 'profile-elder', 'reference', 0),
    ('长者联系人读取', 'elder:profile-elder-contact:read', 'elder', 'profile-elder-contact', 'read', 0),
    ('长者联系人创建', 'elder:profile-elder-contact:create', 'elder', 'profile-elder-contact', 'create', 0),
    ('长者联系人更新', 'elder:profile-elder-contact:update', 'elder', 'profile-elder-contact', 'update', 0),
    ('长者联系人删除', 'elder:profile-elder-contact:delete', 'elder', 'profile-elder-contact', 'delete', 0),
    ('长者联系人导出', 'elder:profile-elder-contact:export', 'elder', 'profile-elder-contact', 'export', 0),
    ('长者联系人引用', 'elder:profile-elder-contact:reference', 'elder', 'profile-elder-contact', 'reference', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 家属账号持有 read 后仍需命中本人范围或动态 ReBAC 授权关系，不能读取未授权长者。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code = 'elder:profile-elder:read'
WHERE role.code IN ('user', 'guest', 'member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- 运营角色维护长者与联系人；后续新增的养老运营角色可在自己的 seed 分段复用这些权限码。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:profile-elder:read',
        'elder:profile-elder:create',
        'elder:profile-elder:update',
        'elder:profile-elder:delete',
        'elder:profile-elder:export',
        'elder:profile-elder:reference',
        'elder:profile-elder-contact:read',
        'elder:profile-elder-contact:create',
        'elder:profile-elder-contact:update',
        'elder:profile-elder-contact:delete',
        'elder:profile-elder-contact:export',
        'elder:profile-elder-contact:reference'
    )
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- ==================== #12004 profile：L3 与 ReBAC 规则 ====================

-- 长者本人由 PersonalScope(userId) 与同值 L3 规则命中；组织管理员只看所属组织。
-- 联系人绑定账号且授权范围非空时，服务层事务性写入：
-- elder:{elderId}#FAMILY@USER:{userId} 及每个授权范围关系（如 VIEW_PROFILE）。
-- ElderResource 的单条 GET 只接受 VIEW_PROFILE，FAMILY 本身不直接扩大读取范围。
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('elder', '["*"]',
     '{"field":"userId","op":"eq","value":"$user.id"}', 'allow'),
    ('elder', '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-contact', '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

-- ==================== #12004 profile：养老菜单 ====================

-- EntityDef 负责通用模块内的实体分组；sys_menu 负责主侧边栏。两边统一使用「养老」。
INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT NULL, '养老', NULL, 'heart-handshake', 34, 'GROUP', TRUE
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
);

WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       item.title,
       item.path,
       item.icon,
       item.sort_order,
       'MENU',
       TRUE,
       item.permission_code
FROM elder_group
CROSS JOIN (
    VALUES
        ('长者档案', '/module/elder', 'heart-handshake', 0, 'elder:profile-elder:read'),
        ('长者联系人', '/module/elder-contact', 'contact-round', 1, 'elder:profile-elder-contact:read')
) AS item(title, path, icon, sort_order, permission_code)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = item.path AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path IN ('/module/elder', '/module/elder-contact')
       OR (menu.parent_id IS NULL AND menu.title = '养老')
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;



-- ============================================================
-- AAF-120 #12005 profile：健康条目与用药权限
-- ============================================================

-- ==================== #12005 profile：L1 权限码 ====================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('健康条目读取', 'elder:profile-health-item:read', 'elder', 'profile-health-item', 'read', 0),
    ('健康条目创建', 'elder:profile-health-item:create', 'elder', 'profile-health-item', 'create', 0),
    ('健康条目更新', 'elder:profile-health-item:update', 'elder', 'profile-health-item', 'update', 0),
    ('健康条目删除', 'elder:profile-health-item:delete', 'elder', 'profile-health-item', 'delete', 0),
    ('健康条目导出', 'elder:profile-health-item:export', 'elder', 'profile-health-item', 'export', 0),
    ('健康条目引用', 'elder:profile-health-item:reference', 'elder', 'profile-health-item', 'reference', 0),
    ('用药计划读取', 'elder:profile-medication-plan:read', 'elder', 'profile-medication-plan', 'read', 0),
    ('用药计划创建', 'elder:profile-medication-plan:create', 'elder', 'profile-medication-plan', 'create', 0),
    ('用药计划更新', 'elder:profile-medication-plan:update', 'elder', 'profile-medication-plan', 'update', 0),
    ('用药计划删除', 'elder:profile-medication-plan:delete', 'elder', 'profile-medication-plan', 'delete', 0),
    ('用药计划导出', 'elder:profile-medication-plan:export', 'elder', 'profile-medication-plan', 'export', 0),
    ('用药计划引用', 'elder:profile-medication-plan:reference', 'elder', 'profile-medication-plan', 'reference', 0),
    ('服药记录读取', 'elder:profile-medication-record:read', 'elder', 'profile-medication-record', 'read', 0),
    ('服药记录创建', 'elder:profile-medication-record:create', 'elder', 'profile-medication-record', 'create', 0),
    ('服药记录导出', 'elder:profile-medication-record:export', 'elder', 'profile-medication-record', 'export', 0),
    ('服药记录引用', 'elder:profile-medication-record:reference', 'elder', 'profile-medication-record', 'reference', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 运营角色维护健康条目和计划；服药记录在管理端只读，创建权限供受控业务入口使用。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:profile-health-item:read',
        'elder:profile-health-item:create',
        'elder:profile-health-item:update',
        'elder:profile-health-item:delete',
        'elder:profile-health-item:export',
        'elder:profile-health-item:reference',
        'elder:profile-medication-plan:read',
        'elder:profile-medication-plan:create',
        'elder:profile-medication-plan:update',
        'elder:profile-medication-plan:delete',
        'elder:profile-medication-plan:export',
        'elder:profile-medication-plan:reference',
        'elder:profile-medication-record:read',
        'elder:profile-medication-record:create',
        'elder:profile-medication-record:export',
        'elder:profile-medication-record:reference'
    )
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- ==================== #12005 profile：L3 组织范围 ====================

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('elder', '["elder_operator"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-contact', '["elder_operator"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('health-item', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('medication-plan', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('medication-record', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

-- ==================== #12005 profile：养老菜单项 ====================

WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       item.title,
       item.path,
       item.icon,
       item.sort_order,
       'MENU',
       TRUE,
       item.permission_code
FROM elder_group
CROSS JOIN (
    VALUES
        ('健康条目', '/module/health-item', 'heart-pulse', 3, 'elder:profile-health-item:read'),
        ('用药计划', '/module/medication-plan', 'pill', 4, 'elder:profile-medication-plan:read'),
        ('服药记录', '/module/medication-record', 'clipboard-check', 5, 'elder:profile-medication-record:read')
) AS item(title, path, icon, sort_order, permission_code)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = item.path AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path IN ('/module/health-item', '/module/medication-plan', '/module/medication-record')
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;

-- ============================================================
-- AAF-120 #12018 rehab：康复指导权限与菜单
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('康复指导读取', 'elder:rehab-guide:read', 'elder', 'rehab-guide', 'read', 0),
    ('康复指导创建', 'elder:rehab-guide:create', 'elder', 'rehab-guide', 'create', 0),
    ('康复指导更新', 'elder:rehab-guide:update', 'elder', 'rehab-guide', 'update', 0),
    ('康复指导删除', 'elder:rehab-guide:delete', 'elder', 'rehab-guide', 'delete', 0),
    ('康复指导导出', 'elder:rehab-guide:export', 'elder', 'rehab-guide', 'export', 0),
    ('康复指导引用', 'elder:rehab-guide:reference', 'elder', 'rehab-guide', 'reference', 0)
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
        'elder:rehab-guide:read',
        'elder:rehab-guide:create',
        'elder:rehab-guide:update',
        'elder:rehab-guide:delete',
        'elder:rehab-guide:export',
        'elder:rehab-guide:reference'
    )
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('rehab-guide', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

-- 康复指导继续复用既有「养老」菜单组。
WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       '康复指导',
       '/module/rehab-guide',
       'accessibility',
       8,
       'MENU',
       TRUE,
       'elder:rehab-guide:read'
FROM elder_group
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = '/module/rehab-guide' AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path = '/module/rehab-guide'
       OR (menu.parent_id IS NULL AND menu.title = '养老')
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;



-- ============================================================
-- AAF-120 #12010 guard：预警权限与菜单
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('养老预警读取', 'elder:guard-alert:read', 'elder', 'guard-alert', 'read', 0),
    ('养老预警处置', 'elder:guard-alert:update', 'elder', 'guard-alert', 'update', 0),
    ('养老预警导出', 'elder:guard-alert:export', 'elder', 'guard-alert', 'export', 0),
    ('养老预警引用', 'elder:guard-alert:reference', 'elder', 'guard-alert', 'reference', 0),
    ('预警处理记录读取', 'elder:guard-alert-handling:read', 'elder', 'guard-alert-handling', 'read', 0),
    ('预警处理记录导出', 'elder:guard-alert-handling:export', 'elder', 'guard-alert-handling', 'export', 0),
    ('预警处理记录引用', 'elder:guard-alert-handling:reference', 'elder', 'guard-alert-handling', 'reference', 0)
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
        'elder:guard-alert:read',
        'elder:guard-alert:update',
        'elder:guard-alert:export',
        'elder:guard-alert:reference',
        'elder:guard-alert-handling:read',
        'elder:guard-alert-handling:export',
        'elder:guard-alert-handling:reference'
    )
WHERE role.code IN ('elder_duty', 'elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('elder-alert', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-alert-handling', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-alert', '["elder_duty"]',
     '{"field":"id","op":"gt","value":0}', 'allow'),
    ('elder-alert-handling', '["elder_duty"]',
     '{"field":"id","op":"gt","value":0}', 'allow')
ON CONFLICT DO NOTHING;

WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       '预警管理',
       '/module/elder-alert',
       'triangle-alert',
       6,
       'MENU',
       TRUE,
       'elder:guard-alert:read'
FROM elder_group
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = '/module/elder-alert' AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path = '/module/elder-alert'
       OR (menu.parent_id IS NULL AND menu.title = '养老')
WHERE role.code IN ('elder_duty', 'elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;



-- ============================================================
-- AAF-120 #12014 album：相册权限与菜单
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('长者照片读取', 'elder:album-photo:read', 'elder', 'album-photo', 'read', 0),
    ('长者照片更新', 'elder:album-photo:update', 'elder', 'album-photo', 'update', 0),
    ('长者照片删除', 'elder:album-photo:delete', 'elder', 'album-photo', 'delete', 0),
    ('长者照片引用', 'elder:album-photo:reference', 'elder', 'album-photo', 'reference', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 设备所有者可在个人范围内供家属助手查询最近照片；管理写操作仅授予运营角色。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code = 'elder:album-photo:read'
WHERE role.code IN ('user', 'guest', 'member', 'elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:album-photo:update',
        'elder:album-photo:delete',
        'elder:album-photo:reference'
    )
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('elder-photo', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

-- 相册继续复用既有「养老」菜单组。
WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       '相册',
       '/module/elder-photo',
       'images',
       10,
       'MENU',
       TRUE,
       'elder:album-photo:read'
FROM elder_group
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = '/module/elder-photo' AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path = '/module/elder-photo'
       OR (menu.parent_id IS NULL AND menu.title = '养老')
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;


-- ============================================================
-- AAF-120 #12013 运营管理端一致性收口
-- 值班角色可查看全部养老运营页面，仅预警资源保留处置权限。
-- ============================================================

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:profile-elder:read',
        'elder:profile-elder-contact:read',
        'elder:profile-health-item:read',
        'elder:profile-medication-plan:read',
        'elder:profile-medication-record:read',
        'device:device:read',
        'elder:guard-alert:read',
        'elder:guard-alert-handling:read',
        'elder:album-photo:read',
        'elder:rehab-guide:read'
    )
WHERE role.code = 'elder_duty'
ON CONFLICT DO NOTHING;

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('elder', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-contact', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('health-item', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('medication-plan', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('medication-record', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('device', '["elder_duty","elder_operator"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('elder-photo', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('rehab-guide', '["elder_duty"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       '预警处理记录',
       '/module/elder-alert-handling',
       'list-checks',
       11,
       'MENU',
       TRUE,
       'elder:guard-alert-handling:read'
FROM elder_group
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = '/module/elder-alert-handling' AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path IN (
        '/module/elder',
        '/module/elder-contact',
        '/module/device',
        '/module/health-item',
        '/module/medication-plan',
        '/module/medication-record',
        '/module/elder-alert',
        '/module/elder-alert-handling',
        '/module/elder-photo',
        '/module/rehab-guide'
    )
    OR (menu.parent_id IS NULL AND menu.title IN ('养老', '设备管理'))
WHERE role.code IN ('elder_operator', 'elder_duty', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;


-- ============================================================
-- AAF-120 #12015 mall：服务项目与预约权限、菜单
-- ============================================================

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('服务项目读取', 'elder:mall-service-item:read', 'elder', 'mall-service-item', 'read', 0),
    ('服务项目创建', 'elder:mall-service-item:create', 'elder', 'mall-service-item', 'create', 0),
    ('服务项目更新', 'elder:mall-service-item:update', 'elder', 'mall-service-item', 'update', 0),
    ('服务项目删除', 'elder:mall-service-item:delete', 'elder', 'mall-service-item', 'delete', 0),
    ('服务项目导出', 'elder:mall-service-item:export', 'elder', 'mall-service-item', 'export', 0),
    ('服务项目引用', 'elder:mall-service-item:reference', 'elder', 'mall-service-item', 'reference', 0),
    ('服务预约读取', 'elder:mall-service-order:read', 'elder', 'mall-service-order', 'read', 0),
    ('服务预约创建', 'elder:mall-service-order:create', 'elder', 'mall-service-order', 'create', 0),
    ('服务预约更新', 'elder:mall-service-order:update', 'elder', 'mall-service-order', 'update', 0),
    ('服务预约删除', 'elder:mall-service-order:delete', 'elder', 'mall-service-order', 'delete', 0),
    ('服务预约导出', 'elder:mall-service-order:export', 'elder', 'mall-service-order', 'export', 0),
    ('服务预约引用', 'elder:mall-service-order:reference', 'elder', 'mall-service-order', 'reference', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

-- 普通账号可浏览目录并创建、读取本人预约；BOOK_SERVICE 授权由 mall 服务再次校验。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:mall-service-item:read',
        'elder:mall-service-item:reference',
        'elder:mall-service-order:read',
        'elder:mall-service-order:create'
    )
WHERE role.code IN ('user', 'guest', 'member', 'elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- 预约确认、取消和完成均依赖 update 权限，只授予运营与管理员。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'elder:mall-service-item:create',
        'elder:mall-service-item:update',
        'elder:mall-service-item:delete',
        'elder:mall-service-item:export',
        'elder:mall-service-order:update',
        'elder:mall-service-order:delete',
        'elder:mall-service-order:export',
        'elder:mall-service-order:reference'
    )
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('service-item', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow'),
    ('service-order', '["elder_operator","org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}', 'allow')
ON CONFLICT DO NOTHING;

-- mall 继续复用既有「养老」菜单组。
WITH elder_group AS (
    SELECT id
    FROM sys_menu
    WHERE parent_id IS NULL AND title = '养老' AND deleted = FALSE
)
INSERT INTO sys_menu
    (parent_id, title, path, icon, sort_order, menu_type, visible, permission_code)
SELECT elder_group.id,
       item.title,
       item.path,
       item.icon,
       item.sort_order,
       'MENU',
       TRUE,
       item.permission_code
FROM elder_group
CROSS JOIN (
    VALUES
        ('服务项目', '/module/service-item', 'shopping-bag', 7, 'elder:mall-service-item:read'),
        ('服务预约', '/module/service-order', 'calendar-check', 9, 'elder:mall-service-order:read')
) AS item(title, path, icon, sort_order, permission_code)
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_menu menu
    WHERE menu.path = item.path AND menu.deleted = FALSE
);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path IN ('/module/service-item', '/module/service-order')
       OR (menu.parent_id IS NULL AND menu.title = '养老')
WHERE role.code IN ('elder_operator', 'org_admin', 'admin', 'super_admin')
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;


-- ============================================================
-- AAF-120 #12017 agent：养老助理工具目录
-- 工具不声明额外功能权限码：角色白名单限定能力，各域 API 再校验 owner、ReBAC 与租户范围。
-- ============================================================

INSERT INTO ai_tool_catalog (
    tool_name, source, enabled, tool_type, category, risk_level,
    read_only, reversible, idempotency_required, require_confirm,
    permission_code, entitlement_code, cost_expression,
    input_schema, output_schema, sort_order, create_time, update_time
) VALUES
    ('queryElderOverview', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     400, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('listAvailableServices', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     410, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('draftServiceOrder', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"required":["serviceItemId","scheduledAt","serviceAddress"],"properties":{"serviceItemId":{"type":"integer"},"quantity":{"type":"integer","minimum":1,"default":1},"scheduledAt":{"type":"string","format":"date-time"},"serviceAddress":{"type":"string","minLength":1,"maxLength":500},"notes":{"type":"string","maxLength":1000}}}', NULL,
     420, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('confirmServiceOrder', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'HIGH',
     FALSE, FALSE, TRUE, TRUE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"required":["draftId"],"properties":{"draftId":{"type":"string","format":"uuid"}}}', NULL,
     430, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('queryServiceOrderStatus', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     440, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('queryAlertStatus', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     450, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('queryDeviceStatus', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     460, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('listMedicationPlans', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{}}', NULL,
     470, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('draftMedicationPlan', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     FALSE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"required":["medicationName","reminderTimes"],"properties":{"medicationName":{"type":"string","minLength":1,"maxLength":200},"dosageInstructions":{"type":"string","maxLength":500},"reminderTimes":{"type":"array","minItems":1,"maxItems":24,"items":{"type":"string","pattern":"^(?:[01]\\d|2[0-3]):[0-5]\\d$"}},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"}}}', NULL,
     480, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('confirmMedicationPlan', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'HIGH',
     FALSE, FALSE, TRUE, TRUE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"required":["draftId"],"properties":{"draftId":{"type":"string","format":"uuid"}}}', NULL,
     490, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('listRecentPhotos', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'LOW',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"properties":{"limit":{"type":"integer","minimum":1,"maximum":20,"default":10}}}', NULL,
     500, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('searchRehabGuide', 'LOCAL', TRUE, 'FUNCTION', 'ELDER', 'MEDIUM',
     TRUE, FALSE, FALSE, FALSE, NULL, NULL, NULL,
     '{"type":"object","additionalProperties":false,"required":["question"],"properties":{"question":{"type":"string","minLength":1,"maxLength":500}}}', NULL,
     510, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (tool_name) WHERE deleted = FALSE DO UPDATE
SET source = EXCLUDED.source,
    enabled = EXCLUDED.enabled,
    tool_type = EXCLUDED.tool_type,
    category = EXCLUDED.category,
    risk_level = EXCLUDED.risk_level,
    read_only = EXCLUDED.read_only,
    reversible = EXCLUDED.reversible,
    idempotency_required = EXCLUDED.idempotency_required,
    require_confirm = EXCLUDED.require_confirm,
    permission_code = EXCLUDED.permission_code,
    input_schema = EXCLUDED.input_schema,
    output_schema = EXCLUDED.output_schema,
    sort_order = EXCLUDED.sort_order,
    update_time = CURRENT_TIMESTAMP;


-- ============================================================
-- 养老运营角色使用通用设备层的权限与菜单（device 种子 v302 先于本文件执行）
-- ============================================================

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
WHERE role.code = 'elder_operator'
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM sys_role role
JOIN sys_menu menu
    ON menu.path = '/module/device'
       OR (menu.parent_id IS NULL AND menu.title = '设备管理')
WHERE role.code = 'elder_operator'
  AND menu.deleted = FALSE
ON CONFLICT DO NOTHING;
