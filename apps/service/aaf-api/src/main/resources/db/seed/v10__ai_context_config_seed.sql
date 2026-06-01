-- ============================================================
-- AI 上下文压缩系统参数
-- ============================================================

INSERT INTO sys_config (category, config_key, value, default_value, value_type, name, description, visible, editable) VALUES
('ai', 'ai.context.enabled', 'true', 'true', 'boolean', '启用上下文压缩', '模型调用前是否启用上下文预算控制、规则裁剪与摘要压缩', TRUE, TRUE),
('ai', 'ai.context.default_policy', 'balanced', 'balanced', 'string', '默认上下文策略', 'balanced/aggressive/preserve-recent/full-detail', TRUE, TRUE),
('ai', 'ai.context.default_context_window', '128000', '128000', 'integer', '默认上下文窗口', '模型未配置 contextWindow 时使用的默认上下文窗口', TRUE, TRUE),
('ai', 'ai.context.reserved_output_tokens', '4096', '4096', 'integer', '输出 Token 预留', '为模型回复预留的 Token 数', TRUE, TRUE),
('ai', 'ai.context.fixed_prompt_budget', '4000', '4000', 'integer', '固定提示词预算', '为系统提示词、工具定义等固定内容预留的 Token 数', TRUE, TRUE),
('ai', 'ai.context.compression_trigger_ratio', '0.5', '0.5', 'string', '上下文压缩触发比例', '输入 Token 达到可用预算该比例时触发压缩判断', TRUE, TRUE),
('ai', 'ai.context.last_keep', '12', '12', 'integer', '最近消息保留数', '规则裁剪与 AgentScope AutoContextMemory 保护的最近消息数量', TRUE, TRUE),
('ai', 'ai.context.message_threshold', '50', '50', 'integer', '消息数阈值', '消息数量超过该值时进入压缩判断', TRUE, TRUE),
('ai', 'ai.context.large_input_char_threshold', '8000', '8000', 'integer', '大消息字符阈值', '单条消息超过该字符数时优先按规则裁剪', TRUE, TRUE),
('ai', 'ai.context.rule_preview_chars', '1600', '1600', 'integer', '规则裁剪预览长度', '大消息规则裁剪后保留的预览字符数', TRUE, TRUE),
('ai', 'ai.context.enable_summary', 'true', 'true', 'boolean', '启用摘要压缩', '规则裁剪后仍超过预算时是否调用摘要模型', TRUE, TRUE),
('ai', 'ai.context.summary_model_id', NULL, NULL, 'string', '摘要模型 ID', '为空时使用本次主模型；建议配置为低成本快模型', TRUE, TRUE),
('ai', 'ai.context.summary_timeout_ms', '8000', '8000', 'integer', '摘要超时毫秒', '摘要模型调用超时时间', TRUE, TRUE),
('ai', 'ai.context.summary_system_prompt', '你是 AAF 的上下文压缩器。你的任务是压缩历史上下文，保留用户目标、关键事实、已确认决策、约束、未完成事项和必要引用。只输出可继续推理的摘要，不要输出解释。', '你是 AAF 的上下文压缩器。你的任务是压缩历史上下文，保留用户目标、关键事实、已确认决策、约束、未完成事项和必要引用。只输出可继续推理的摘要，不要输出解释。', 'string', '摘要系统提示词', '上下文摘要模型使用的系统提示词', TRUE, TRUE),
('ai', 'ai.context.summary_user_prompt', '请将以下对话上下文压缩到不超过 ${budgetTokens} tokens。\n\n保留：\n- 当前任务目标和用户明确要求\n- 关键业务数据、ID、路径、错误信息\n- 已做决策和不可违反约束\n- 未完成的下一步\n\n可以删除：\n- 重复寒暄\n- 已被后续内容覆盖的中间过程\n- 大段原始数据中的低价值细节\n\n待压缩上下文：\n${messages}', '请将以下对话上下文压缩到不超过 ${budgetTokens} tokens。\n\n保留：\n- 当前任务目标和用户明确要求\n- 关键业务数据、ID、路径、错误信息\n- 已做决策和不可违反约束\n- 未完成的下一步\n\n可以删除：\n- 重复寒暄\n- 已被后续内容覆盖的中间过程\n- 大段原始数据中的低价值细节\n\n待压缩上下文：\n${messages}', 'string', '摘要用户提示词模板', '支持 ${budgetTokens} 和 ${messages} 占位符', TRUE, TRUE)
ON CONFLICT (config_key) DO NOTHING;
