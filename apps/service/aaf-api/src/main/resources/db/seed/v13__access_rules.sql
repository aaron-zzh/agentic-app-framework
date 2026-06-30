-- ============================================================
-- 行级数据权限规则（业务规则，可按需扩展）
-- 管理员/超级管理员自动绕过（isSuperAdmin 逻辑）
-- * 表示所有已登录用户，无需枚举角色
-- ============================================================

INSERT INTO sys_data_access_rule (entity_slug, roles, condition, effect)
VALUES
    ('todo',
     '["*"]',
     '{"field":"assigneeId","op":"eq","value":"$user.id"}',
     'allow'),
    ('notice',
     '["*"]',
     '{"field":"ownerId","op":"eq","value":"$user.id"}',
     'allow'),
    ('generation-template',
     '["*"]',
     '{"or":[{"field":"ownerId","op":"eq","value":"$user.id"},{"field":"isPublic","op":"eq","value":true}]}',
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
