DO $$
DECLARE
    default_model_id BIGINT;
BEGIN
    SELECT id
      INTO default_model_id
      FROM ai_model
     WHERE enabled = TRUE
       AND capabilities LIKE '%CHAT%'
       AND deleted = FALSE
     ORDER BY sort_order ASC, id ASC
     LIMIT 1;

    IF default_model_id IS NULL THEN
        RAISE EXCEPTION '安装 P2 系统 AgentDefinition 前必须存在启用的 CHAT 模型';
    END IF;

    INSERT INTO ai_agent_definition (
        version,
        agent_id,
        name,
        description,
        system_prompt,
        model_id,
        capabilities,
        tools,
        allowed_tools,
        max_iterations,
        timeout_seconds,
        status,
        create_time,
        update_time
    ) VALUES
    (
        1,
        'system.agent.content-creator',
        '内容创作执行 Agent',
        '为系统内容创作 Assistant 生成可审查内容。',
        '你是内容创作执行 Agent。根据用户目标生成清晰、准确、可审查的策划或草稿；不得发布、删除、付费或代表用户对外承诺。',
        default_model_id,
        '["content.plan","content.draft"]',
        '[]',
        '[]',
        10,
        120,
        'active',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ),
    (
        1,
        'system.agent.customer-service',
        '客服执行 Agent',
        '为系统客服 Assistant 提供只读咨询和排查。',
        '你是客服执行 Agent。仅依据用户提供的信息给出准确、简洁的只读咨询与排查建议；未知内容不得猜测，需要人工处理时明确建议转人工。',
        default_model_id,
        '["support.read","support.handoff"]',
        '[]',
        '[]',
        8,
        90,
        'active',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (agent_id) DO UPDATE SET
        version = EXCLUDED.version,
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        system_prompt = EXCLUDED.system_prompt,
        model_id = EXCLUDED.model_id,
        capabilities = EXCLUDED.capabilities,
        tools = EXCLUDED.tools,
        allowed_tools = EXCLUDED.allowed_tools,
        max_iterations = EXCLUDED.max_iterations,
        timeout_seconds = EXCLUDED.timeout_seconds,
        status = EXCLUDED.status,
        update_time = CURRENT_TIMESTAMP;
END $$;
