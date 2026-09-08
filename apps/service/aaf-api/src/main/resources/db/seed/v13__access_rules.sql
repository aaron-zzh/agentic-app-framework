-- ============================================================
-- 行级数据权限规则（业务规则，可按需扩展）
-- 管理员/超级管理员自动绕过（isSuperAdmin 逻辑）
-- * 表示所有已登录用户，无需枚举角色
-- ============================================================

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('workspace',
     '["*"]',
     '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"id","op":"in","value":"$user.workspaceIds"}]}',
     'allow'),
    ('todo',
     '["*"]',
     '{"field":"assigneeId","op":"eq","value":"$user.id"}',
     'allow'),
    ('notice',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;


-- aigc 相关实体：登录用户只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('aigc-project',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-content',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-shot',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-storyboard',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-timeline',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;

-- 对话、工单、克隆声音：登录用户只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('conversation',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ticket',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ai-cloned-voice',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;


-- aigc-task、ai-digital-avatar：登录用户只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('aigc-task',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ai-digital-avatar',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;

-- 资产中心同接口数据范围：Studio 使用 X-Scope: own，中后台使用 X-Scope: all。
-- PersonalScope 负责默认个人视角；L3 规则防止普通成员通过伪造 all Header 越权。

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('asset',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('asset-category',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('asset-tag',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('asset-collection',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('asset',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow'),
    ('asset-category',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow'),
    ('asset-tag',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow'),
    ('asset-collection',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow')
ON CONFLICT DO NOTHING;

-- 创作片段同接口数据范围：成员可读取自己的片段和公开片段，写操作仍由服务层校验所有权。

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('snippet',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('snippet',
     '["*"]',
     '{"field":"isPublic","op":"eq","value":true}',
     'allow'),
    ('snippet',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow')
ON CONFLICT DO NOTHING;

-- 品牌资料同接口数据范围：普通成员即使伪造 X-Scope: all 也只能访问自己的品牌资料。

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('brand-profile',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('brand-profile',
     '["org_admin","admin"]',
     '{"field":"orgId","op":"in","value":"$user.orgIds"}',
     'allow')
ON CONFLICT DO NOTHING;

-- AIGC 项目创建权限与记录规则
-- 创建/删除/归档只做"是否已登录"的门槛（BaseCrudController 已统一 isAuthenticated）；
-- 具体能看到/操作哪些记录由下方 L3 记录规则控制：普通用户仅本人项目，管理员全量。

-- 所有人类登录角色均可创建 AIGC 项目，用于前端 EntityAccess.create 展示判断。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission ON permission.code = 'aigc:project:create'
WHERE role.code IN ('user', 'guest', 'member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- ---------------- L3 项目记录范围 ----------------
-- 普通登录用户只读/写自己的项目；admin/super_admin 全量可见。
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('project', '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}', 'allow'),
    ('project', '["admin", "super_admin"]',
     '{"field":"id","op":"gt","value":0}', 'allow')
ON CONFLICT DO NOTHING;
