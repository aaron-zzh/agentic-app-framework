-- AAF-043: 对话引擎增强 - 新增 AI 对话相关字段
-- 会话表增加 AI 相关字段
ALTER TABLE sys_chat_session ADD COLUMN IF NOT EXISTS agent_id VARCHAR(100);
ALTER TABLE sys_chat_session ADD COLUMN IF NOT EXISTS model_id VARCHAR(100);
ALTER TABLE sys_chat_session ADD COLUMN IF NOT EXISTS total_tokens BIGINT DEFAULT 0;

-- 消息表增加 Token 计数和元数据
ALTER TABLE sys_chat_message ADD COLUMN IF NOT EXISTS token_count INTEGER;
ALTER TABLE sys_chat_message ADD COLUMN IF NOT EXISTS metadata TEXT;
