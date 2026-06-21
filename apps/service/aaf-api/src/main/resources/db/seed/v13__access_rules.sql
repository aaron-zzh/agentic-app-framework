-- ============================================================
-- 行级数据权限规则（业务规则，可按需扩展）
-- 管理员/超级管理员自动绕过（isSuperAdmin 逻辑）
-- ============================================================

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('todo',
     '["member","guest","sales","agent"]',
     '{"field":"assigneeId","op":"eq","value":"$user.id"}',
     'allow'),
    ('notification',
     '["member","guest","sales","agent"]',
     '{"field":"userId","op":"eq","value":"$user.id"}',
     'allow'),
    ('generation-template',
     '["member","guest","sales","agent"]',
     '{"or":[{"field":"userId","op":"eq","value":"$user.id"},{"field":"isPublic","op":"eq","value":true}]}',
     'allow')
ON CONFLICT DO NOTHING;
