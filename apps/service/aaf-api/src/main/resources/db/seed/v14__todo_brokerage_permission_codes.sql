-- ============================================================
-- 待办事项与分销管理权限补充：L1 权限码、角色授权与 Todo L3 规则
-- ============================================================

-- ---------------- 待办事项功能权限码（L1） ----------------
-- 显式注册 Todo 全部已声明动作权限和两种受控 AccessMode，不依赖缺码 fallback。

INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('待办读取', 'system:todo:read', 'system', 'todo', 'read', 0),
    ('待办创建', 'system:todo:create', 'system', 'todo', 'create', 0),
    ('待办更新', 'system:todo:update', 'system', 'todo', 'update', 0),
    ('待办删除', 'system:todo:delete', 'system', 'todo', 'delete', 0),
    ('待办导出', 'system:todo:export', 'system', 'todo', 'export', 0),
    ('待办引用', 'system:todo:reference', 'system', 'todo', 'reference', 0),
    ('待办管理维护模式', 'system:todo:access-mode:admin-maintenance', 'system', 'todo', 'admin-maintenance', 0),
    ('待办系统任务模式', 'system:todo:access-mode:system-job', 'system', 'todo', 'system-job', 0)
ON CONFLICT (code) DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE r.code IN ('member', 'org_admin', 'admin', 'super_admin')
  AND p.code IN (
      'system:todo:read',
      'system:todo:create',
      'system:todo:update',
      'system:todo:delete',
      'system:todo:export',
      'system:todo:reference'
  )
ON CONFLICT DO NOTHING;

-- AccessMode 必须持有专用权限；SYSTEM_JOB 还要求内部执行上下文，不能由普通 HTTP 请求直接使用。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission_code p
WHERE (p.code = 'system:todo:access-mode:admin-maintenance'
       AND r.code IN ('admin', 'super_admin'))
   OR (p.code = 'system:todo:access-mode:system-job'
       AND r.code IN ('member', 'org_admin', 'admin', 'super_admin'))
ON CONFLICT DO NOTHING;

-- ---------------- 分销管理功能权限码（L1） ----------------
-- 管理接口与敏感提现数据仅授予 admin / super_admin；普通用户只保留 /brokerage/me/* 自助流程。
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('分销员读取', 'brokerage:brokerage-user:read', 'brokerage', 'brokerage-user', 'read', 0),
    ('分销员更新', 'brokerage:brokerage-user:update', 'brokerage', 'brokerage-user', 'update', 0),
    ('分销员导出', 'brokerage:brokerage-user:export', 'brokerage', 'brokerage-user', 'export', 0),
    ('佣金流水读取', 'brokerage:brokerage-record:read', 'brokerage', 'brokerage-record', 'read', 0),
    ('佣金流水导出', 'brokerage:brokerage-record:export', 'brokerage', 'brokerage-record', 'export', 0),
    ('佣金提现读取', 'brokerage:brokerage-withdraw:read', 'brokerage', 'brokerage-withdraw', 'read', 0),
    ('佣金提现导出', 'brokerage:brokerage-withdraw:export', 'brokerage', 'brokerage-withdraw', 'export', 0),
    ('佣金提现审核通过', 'brokerage:brokerage-withdraw:approve', 'brokerage', 'brokerage-withdraw', 'approve', 0),
    ('佣金提现驳回', 'brokerage:brokerage-withdraw:reject', 'brokerage', 'brokerage-withdraw', 'reject', 0),
    ('佣金提现转账确认', 'brokerage:brokerage-withdraw:transfer-confirm', 'brokerage', 'brokerage-withdraw', 'transfer-confirm', 0),
    ('佣金规则读取', 'brokerage:brokerage-rule:read', 'brokerage', 'brokerage-rule', 'read', 0),
    ('佣金规则创建', 'brokerage:brokerage-rule:create', 'brokerage', 'brokerage-rule', 'create', 0),
    ('佣金规则更新', 'brokerage:brokerage-rule:update', 'brokerage', 'brokerage-rule', 'update', 0),
    ('佣金规则删除', 'brokerage:brokerage-rule:delete', 'brokerage', 'brokerage-rule', 'delete', 0),
    ('佣金规则导出', 'brokerage:brokerage-rule:export', 'brokerage', 'brokerage-rule', 'export', 0),
    ('等级佣金加成读取', 'brokerage:brokerage-level-bonus:read', 'brokerage', 'brokerage-level-bonus', 'read', 0),
    ('等级佣金加成创建', 'brokerage:brokerage-level-bonus:create', 'brokerage', 'brokerage-level-bonus', 'create', 0),
    ('等级佣金加成更新', 'brokerage:brokerage-level-bonus:update', 'brokerage', 'brokerage-level-bonus', 'update', 0),
    ('等级佣金加成删除', 'brokerage:brokerage-level-bonus:delete', 'brokerage', 'brokerage-level-bonus', 'delete', 0),
    ('等级佣金加成导出', 'brokerage:brokerage-level-bonus:export', 'brokerage', 'brokerage-level-bonus', 'export', 0)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    module = EXCLUDED.module,
    resource = EXCLUDED.resource,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission_code p
    ON p.code IN (
        'brokerage:brokerage-user:read',
        'brokerage:brokerage-user:update',
        'brokerage:brokerage-user:export',
        'brokerage:brokerage-record:read',
        'brokerage:brokerage-record:export',
        'brokerage:brokerage-withdraw:read',
        'brokerage:brokerage-withdraw:export',
        'brokerage:brokerage-withdraw:approve',
        'brokerage:brokerage-withdraw:reject',
        'brokerage:brokerage-withdraw:transfer-confirm',
        'brokerage:brokerage-rule:read',
        'brokerage:brokerage-rule:create',
        'brokerage:brokerage-rule:update',
        'brokerage:brokerage-rule:delete',
        'brokerage:brokerage-rule:export',
        'brokerage:brokerage-level-bonus:read',
        'brokerage:brokerage-level-bonus:create',
        'brokerage:brokerage-level-bonus:update',
        'brokerage:brokerage-level-bonus:delete',
        'brokerage:brokerage-level-bonus:export'
    )
WHERE r.code IN ('admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- ---------------- 分销管理菜单与角色授权 ----------------
-- v12 已创建四个基础分销菜单；此处将佣金规则切换到通用模块，并补充等级佣金加成。
UPDATE sys_menu
SET path = '/module/brokerage-rule',
    update_time = CURRENT_TIMESTAMP
WHERE path = '/admin/brokerage/rules'
  AND deleted = FALSE;

INSERT INTO sys_menu (parent_id, title, path, icon, sort_order, menu_type, visible)
SELECT parent_menu.id, '等级佣金加成', '/module/brokerage-level-bonus', 'badge-percent', 4, 'MENU', TRUE
FROM sys_menu parent_menu
WHERE parent_menu.title = '分销'
  AND parent_menu.parent_id IS NULL
  AND parent_menu.deleted = FALSE
  AND NOT EXISTS (
      SELECT 1
      FROM sys_menu existing_menu
      WHERE existing_menu.path = '/module/brokerage-level-bonus'
        AND existing_menu.deleted = FALSE
  );

-- 分销管理模块仅授权 admin / super_admin；撤销 v12 全量菜单授权遗留的 org_admin 映射。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m
    ON m.path IN (
        '/module/brokerage-user',
        '/module/brokerage-record',
        '/module/brokerage-withdraw',
        '/module/brokerage-rule',
        '/module/brokerage-level-bonus'
    )
WHERE r.code IN ('admin', 'super_admin')
  AND m.deleted = FALSE
ON CONFLICT DO NOTHING;

DELETE FROM sys_role_menu role_menu
USING sys_role r, sys_menu m
WHERE role_menu.role_id = r.id
  AND role_menu.menu_id = m.id
  AND r.code = 'org_admin'
  AND m.path IN (
      '/module/brokerage-user',
      '/module/brokerage-record',
      '/module/brokerage-withdraw',
      '/module/brokerage-rule',
      '/module/brokerage-level-bonus'
  );

-- ---------------- 行级数据权限规则扩展（L3） ----------------
-- org_admin 不受 assigneeId 行级限制，可查看组织内所有人的待办，但仍需限定在自己所属组织内，
-- 不能越权看到其他组织的数据（纵深防御，不依赖 orgFilter 单层兜底）。
-- 与既有 ["*"] 规则按 OR 合并（DataAccessService.buildNormalizedDomain），
-- 命中该角色的用户最终条件为 (assigneeId = $user.id) OR (orgId in $user.orgIds)。
-- 用户可同时属于多个组织，因此使用集合变量和 in 操作。

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('todo',
     '["org_admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow')
ON CONFLICT DO NOTHING;
