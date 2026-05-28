-- v8: ai_assistant 表添加委托权限字段
ALTER TABLE ai_assistant ADD COLUMN IF NOT EXISTS delegator_id BIGINT;
ALTER TABLE ai_assistant ADD COLUMN IF NOT EXISTS permission_scope JSONB;

COMMENT ON COLUMN ai_assistant.delegator_id IS '委托者 ID（权限继承来源，默认等于 user_id）';
COMMENT ON COLUMN ai_assistant.permission_scope IS '权限边界配置（JSON：allowedTools/allowedResources/allowedOperations/maxAutoRiskLevel/overLimitAction）';

-- 回填：delegator_id 默认等于 user_id
UPDATE ai_assistant SET delegator_id = user_id WHERE delegator_id IS NULL;
