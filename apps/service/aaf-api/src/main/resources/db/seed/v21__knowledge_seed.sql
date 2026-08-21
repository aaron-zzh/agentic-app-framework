

-- NexusKB v21：生产必需种子数据。schema 由 v20 完整创建；本迁移另确保哈希函数依赖可用。

CREATE EXTENSION IF NOT EXISTS pgcrypto;

SET LOCAL TIME ZONE 'UTC';

-- 知识库权限显式 seed：普通 CRUD 使用 DEFAULT；后台运维额外要求 ADMIN_MAINTENANCE。
INSERT INTO sys_permission_code (name, code, module, resource, action, status)
VALUES
    ('知识库读取', 'system:knowledge-base:read', 'system', 'knowledge-base', 'read', 0),
    ('知识库创建', 'system:knowledge-base:create', 'system', 'knowledge-base', 'create', 0),
    ('知识库更新', 'system:knowledge-base:update', 'system', 'knowledge-base', 'update', 0),
    ('知识库删除', 'system:knowledge-base:delete', 'system', 'knowledge-base', 'delete', 0),
    ('知识库导入', 'system:knowledge-base:import', 'system', 'knowledge-base', 'import', 0),
    ('知识库导出', 'system:knowledge-base:export', 'system', 'knowledge-base', 'export', 0),
    ('知识库引用', 'system:knowledge-base:reference', 'system', 'knowledge-base', 'reference', 0),
    ('知识库管理维护模式', 'system:knowledge-base:access-mode:admin-maintenance',
     'system', 'knowledge-base', 'admin-maintenance', 0)
ON CONFLICT (code) WHERE deleted = FALSE DO NOTHING;

-- 修正重复执行或曾应用旧 seed 后遗留的只读角色写权限。
DELETE FROM sys_role_permission role_permission
USING sys_role role, sys_permission_code permission
WHERE role_permission.role_id = role.id
  AND role_permission.permission_id = permission.id
  AND role.code IN ('user', 'guest')
  AND permission.code IN (
      'system:knowledge-base:create',
      'system:knowledge-base:update',
      'system:knowledge-base:delete',
      'system:knowledge-base:import'
  );

-- 普通成员与管理员可维护知识库。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'system:knowledge-base:read',
        'system:knowledge-base:create',
        'system:knowledge-base:update',
        'system:knowledge-base:delete',
        'system:knowledge-base:import',
        'system:knowledge-base:export',
        'system:knowledge-base:reference'
    )
WHERE role.code IN ('member', 'org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;

-- user/guest 保持只读，可查询、导出并作为引用选择项使用。
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code IN (
        'system:knowledge-base:read',
        'system:knowledge-base:export',
        'system:knowledge-base:reference'
    )
WHERE role.code IN ('user', 'guest')
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission_code permission
    ON permission.code = 'system:knowledge-base:access-mode:admin-maintenance'
WHERE role.code IN ('org_admin', 'admin', 'super_admin')
ON CONFLICT DO NOTHING;



-- 知识入库只读取 SYSTEM 模型偏好；初始值复用当前系统 CHAT 偏好。
WITH selected AS (
    SELECT COALESCE(
        (SELECT model_ids FROM ai_model_preference
         WHERE scope = 'SYSTEM' AND scope_id IS NULL AND capability = 'CHAT'
         LIMIT 1),
        (SELECT jsonb_build_array(model_id) FROM ai_model
         WHERE enabled = TRUE AND capabilities LIKE '%CHAT%'
         ORDER BY id LIMIT 1)) AS model_ids
), capabilities(capability) AS (
    VALUES ('KNOWLEDGE_EXTRACTION'), ('KNOWLEDGE_ENTITY_RESOLUTION')
)
INSERT INTO ai_model_preference (scope, scope_id, capability, model_ids)
SELECT 'SYSTEM', NULL, capabilities.capability, selected.model_ids
FROM selected CROSS JOIN capabilities
WHERE selected.model_ids IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM ai_model_preference existing
      WHERE existing.scope = 'SYSTEM'
        AND existing.scope_id IS NULL
        AND existing.capability = capabilities.capability
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM ai_model_preference
        WHERE scope = 'SYSTEM' AND scope_id IS NULL
          AND capability = 'KNOWLEDGE_EXTRACTION'
    ) OR NOT EXISTS (
        SELECT 1 FROM ai_model_preference
        WHERE scope = 'SYSTEM' AND scope_id IS NULL
          AND capability = 'KNOWLEDGE_ENTITY_RESOLUTION'
    ) THEN
        RAISE EXCEPTION 'v21 无法初始化知识抽取模型偏好：缺少已启用 CHAT 模型';
    END IF;
END $$;

-- 平台向导知识库（公共，auto_inject=false，由 search_kb 工具按需检索）。
INSERT INTO ai_knowledge_base (
    id, stable_id, visibility, scope_code, name, description, embedding_model,
    chunk_strategy, chunk_size, chunk_overlap, status, auto_inject,
    projection_watermark, owner_id, create_time, update_time, deleted)
VALUES (
    1, '00000000-0000-0000-0000-000000000301', 'SYSTEM_PUBLIC',
    'system:aaf-platform-guide', 'AAF 平台向导知识库',
    '产品咨询、常见问题和功能说明，供默认用户助理的平台向导 Role 使用',
    'text-embedding-v3', 'RECURSIVE', 512, 64, 0, FALSE,
    1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_knowledge_document (
    id, stable_id, knowledge_base_id, uploaded_by, source_type, source_key,
    title, file_type, content_hash, status, chunk_count,
    create_time, update_time, deleted)
VALUES (
    1, '00000000-0000-0000-0000-000000000302', 1,
    (SELECT id FROM sys_user WHERE username = 'admin'), 'SYSTEM',
    'builtin:aaf-platform-guide', 'AAF 框架介绍', 'text',
    encode(digest('aaf-platform-guide-v1', 'sha256'), 'hex'), 2, 3,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_knowledge_ingest_run (
    id, knowledge_base_id, document_id, run_no, ingest_fingerprint,
    payer_user_id, status, parser_version, chunk_config_digest,
    extraction_prompt_snapshot, extraction_prompt_digest, extraction_prompt_version,
    extraction_user_prompt_snapshot, extraction_user_prompt_digest, extraction_user_prompt_version,
    extraction_output_contract_version, extraction_model_id,
    entity_resolution_prompt_snapshot, entity_resolution_prompt_digest, entity_resolution_prompt_version,
    entity_resolution_user_prompt_snapshot, entity_resolution_user_prompt_digest, entity_resolution_user_prompt_version,
    entity_resolution_output_contract_version, entity_resolution_model_id,
    normalization_version, embedding_model_id, expected_active_run_id, fencing_token,
    started_at, ready_at, published_at, finished_at)
SELECT
    '00000000-0000-0000-0000-000000000303', 1, 1, 1,
    encode(digest('seed-v21|aaf-platform-guide-v1', 'sha256'), 'hex'),
    (SELECT id FROM sys_user WHERE username = 'admin'),
    'PUBLISHED', 'seed-v21',
    encode(digest('RECURSIVE|512|64', 'sha256'), 'hex'),
    extraction.content, extraction.content_hash, extraction.template_version,
    extraction_user.content, extraction_user.content_hash, extraction_user.template_version,
    'knowledge-fact-schema-v2', extraction_model.model_id,
    resolution.content, resolution.content_hash, resolution.template_version,
    resolution_user.content, resolution_user.content_hash, resolution_user.template_version,
    'knowledge-entity-resolution-schema-v1', resolution_model.model_id,
    'v1', 'text-embedding-v3', NULL, 1,
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM ai_prompt_template extraction_root
JOIN ai_prompt_template_version extraction ON extraction.id = extraction_root.current_version_id
JOIN ai_prompt_template extraction_user_root ON extraction_user_root.code = 'aaf.knowledge.fact-extraction.user' AND extraction_user_root.deleted = FALSE
JOIN ai_prompt_template_version extraction_user ON extraction_user.id = extraction_user_root.current_version_id
JOIN ai_prompt_template resolution_root ON resolution_root.code = 'aaf.knowledge.entity-resolution.system' AND resolution_root.deleted = FALSE
JOIN ai_prompt_template_version resolution ON resolution.id = resolution_root.current_version_id
JOIN ai_prompt_template resolution_user_root ON resolution_user_root.code = 'aaf.knowledge.entity-resolution.user' AND resolution_user_root.deleted = FALSE
JOIN ai_prompt_template_version resolution_user ON resolution_user.id = resolution_user_root.current_version_id
CROSS JOIN LATERAL (
    SELECT model_ids ->> 0 AS model_id
    FROM ai_model_preference
    WHERE scope = 'SYSTEM' AND scope_id IS NULL
      AND capability = 'KNOWLEDGE_EXTRACTION'
    LIMIT 1
) extraction_model
CROSS JOIN LATERAL (
    SELECT model_ids ->> 0 AS model_id
    FROM ai_model_preference
    WHERE scope = 'SYSTEM' AND scope_id IS NULL
      AND capability = 'KNOWLEDGE_ENTITY_RESOLUTION'
    LIMIT 1
) resolution_model
WHERE extraction_root.code = 'aaf.knowledge.fact-extraction.system'
  AND extraction_root.deleted = FALSE
ON CONFLICT (id) DO NOTHING;

WITH seeded_chunks(id, stable_id, content, chunk_index, token_count) AS (
    VALUES
    (1, '00000000-0000-0000-0000-000000000311'::UUID,
     $chunk_1$AAF（Agentic App Framework）是一套面向开发者的生产级 AI 原生应用开发框架，目标是让每一个团队都能快速构建多智能体协作应用，而不需要从零搭建 AI 基础设施。

AAF 的核心理念是「AI 是架构的一等公民」——不是在传统业务系统上贴一层 AI，而是从设计之初就以 AI 协作为中心来组织整个系统。

主要核心能力：
• 多智能体协作：支持 Agent 间的分工、委托与并行执行，内置 ReAct 推理循环、子 Agent 派发、结果汇聚等机制
• 工作流引擎：可视化拖拽设计 AI 工作流，支持 LLM 节点、知识库节点、条件分支、代码节点等，底层由 Flowable 驱动执行
• 知识库管理：支持文档上传、自动分块、向量化存储，提供语义检索（pgvector hnsw）、混合检索和知识图谱能力
• 规范驱动开发：先写规范再写代码，规范是人类和 AI 的共同真理来源，支持 AI 全流程自动开发
• 无代码开发：普通用户可通过可视化界面搭建工作流、配置技能和知识库，无需编写代码$chunk_1$,
     0, 380),
    (2, '00000000-0000-0000-0000-000000000312'::UUID,
     $chunk_2$AAF 技术栈：
• 后端：Java 25 + Spring Boot 4 + Spring AI + WebFlux + GraphQL + MCP 协议
• 智能体框架：AgentScope Java（HarnessAgent，支持 AG-UI 协议流式交互）
• 数据层：PostgreSQL + pgvector（向量检索）、Neo4j（知识图谱）、Redis（缓存）
• 工作流：Flowable（同时支持 AI 编排流和企业审批流）
• 前端：Next.js 16 + React 19 + TypeScript，工程化采用 Nx Monorepo + pnpm
• 跨端：UniApp（微信小程序 / H5 / APP）

整体分为五层架构：
1. 对话与交互层：多端适配、SSE 流式推送、REST/WebSocket/AG-UI 接口
2. 服务层：用户管理、知识库、工作流、计费、AIGC 内容创作等业务模块
3. 智能层：Core/Cognition/Agent/Assistant/Team 五层 AI 协作体系
4. 引擎层：工作流引擎、知识库引擎、记忆引擎、工具系统、MCP 集成
5. 基础设施层：PostgreSQL、Redis、Neo4j、向量库、Agent 沙箱

AAF 支持多种部署方式，生产环境推荐 Docker Compose 或 Kubernetes，本地开发只需 JDK 25 + Node.js 22 + PostgreSQL。$chunk_2$,
     1, 320),
    (3, '00000000-0000-0000-0000-000000000313'::UUID,
     $chunk_3$AAF 适用场景：

1. 企业 AI 助理：基于知识库构建企业专属客服、HR 助手、产品顾问，支持多知识库切换和权限隔离
2. 内容创作平台：集成 AI 写作、图像生成、视频生成能力，支持多平台内容分发（小红书/公众号/抖音）
3. 智能工作流：将重复性业务流程（如文档处理、数据提取、报告生成）自动化，人工只审核关键节点
4. AI 开发工具：支持 AI 辅助编码、代码审查、自动测试，集成 MCP 工具协议对接外部开发工具
5. 多智能体协作：复杂任务由协调者 Agent 拆解后分配给专业子 Agent 并行处理，最终汇总结果

适用人群：
• 希望快速落地 AI 应用的开发团队（节省搭建基础设施的时间）
• 需要结合知识库和业务系统的企业（客服、销售、运营场景）
• 想通过无代码方式构建 AI 工作流的业务人员

当前版本（v0.1.0）已稳定支持：多智能体对话、知识库检索、内容创作工作流、计费与权限管理。
更多功能（Agent 市场、低代码工作流编辑器、多租户SaaS）在 v0.2.0 规划中。

如需了解更多，欢迎访问项目文档或通过客服联系我们。$chunk_3$,
     2, 350)
)
INSERT INTO ai_knowledge_chunk (
    id, stable_id, run_id, document_id, knowledge_base_id, content,
    content_hash, chunk_index, token_count, metadata, created_at)
SELECT id, stable_id, '00000000-0000-0000-0000-000000000303', 1, 1, content,
       encode(digest(content, 'sha256'), 'hex'), chunk_index, token_count,
       jsonb_build_object('seed', 'platform-guide'), CURRENT_TIMESTAMP
FROM seeded_chunks
ON CONFLICT (id) DO NOTHING;

UPDATE ai_knowledge_document
SET active_run_id = '00000000-0000-0000-0000-000000000303',
    ingest_fence = 1,
    status = 2,
    chunk_count = 3,
    update_time = CURRENT_TIMESTAMP
WHERE id = 1;

INSERT INTO ai_knowledge_projection_checkpoint (
    knowledge_base_id, projection_kind, desired_watermark, applied_watermark, status)
VALUES
    (1, 'PGVECTOR', 1, 0, 'DEGRADED'),
    (1, 'NEO4J', 1, 0, 'DEGRADED')
ON CONFLICT (knowledge_base_id, projection_kind) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('ai_knowledge_base', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM ai_knowledge_base), 1), 1), TRUE);
SELECT setval(
    pg_get_serial_sequence('ai_knowledge_document', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM ai_knowledge_document), 1), 1), TRUE);
SELECT setval(
    pg_get_serial_sequence('ai_knowledge_chunk', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM ai_knowledge_chunk), 1), 1), TRUE);
