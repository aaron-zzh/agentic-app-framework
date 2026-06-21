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
    ('notice',
     '["member","guest","sales","agent"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('generation-template',
     '["member","guest","sales","agent"]',
     '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"isPublic","op":"eq","value":true}]}',
     'allow')
ON CONFLICT DO NOTHING;


-- aigc 相关实体：member 只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('aigc-project',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-content',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-shot',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-storyboard',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('aigc-timeline',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;

-- 对话、工单、克隆声音：member 只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('conversation',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ticket',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ai-cloned-voice',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;


-- aigc-task、ai-digital-avatar：member 只能访问自己的数据
INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('aigc-task',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('ai-digital-avatar',
     '["member"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow')
ON CONFLICT DO NOTHING;
