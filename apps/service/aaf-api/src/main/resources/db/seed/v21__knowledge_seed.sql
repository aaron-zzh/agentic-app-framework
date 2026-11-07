

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



-- 知识入库只读取 SYSTEM 模型偏好。
-- 事实抽取使用百炼 JSON Schema strict 结构化输出，只能用百炼模型，固定为 qwen3.8-flash。
INSERT INTO ai_model_preference (scope, scope_id, capability, model_ids)
SELECT 'SYSTEM', NULL, 'KNOWLEDGE_EXTRACTION', jsonb_build_array(model.model_id)
FROM ai_model model
WHERE model.model_id = 'qwen:qwen3.8-flash' AND model.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM ai_model_preference existing
      WHERE existing.scope = 'SYSTEM'
        AND existing.scope_id IS NULL
        AND existing.capability = 'KNOWLEDGE_EXTRACTION'
  );

-- 文档摘要与知识库涵盖主题同样使用百炼 JSON Schema strict 结构化输出，固定为 qwen3.8-flash。
INSERT INTO ai_model_preference (scope, scope_id, capability, model_ids)
SELECT 'SYSTEM', NULL, 'KNOWLEDGE_SUMMARY', jsonb_build_array(model.model_id)
FROM ai_model model
WHERE model.model_id = 'qwen:qwen3.8-flash' AND model.enabled = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM ai_model_preference existing
      WHERE existing.scope = 'SYSTEM'
        AND existing.scope_id IS NULL
        AND existing.capability = 'KNOWLEDGE_SUMMARY'
  );

-- 实体消歧走通用工具调用，初始值复用当前系统 CHAT 偏好。
WITH selected AS (
    SELECT COALESCE(
        (SELECT model_ids FROM ai_model_preference
         WHERE scope = 'SYSTEM' AND scope_id IS NULL AND capability = 'CHAT'
         LIMIT 1),
        (SELECT jsonb_build_array(model_id) FROM ai_model
         WHERE enabled = TRUE AND capabilities LIKE '%CHAT%'
         ORDER BY id LIMIT 1)) AS model_ids
), capabilities(capability) AS (
    VALUES ('KNOWLEDGE_ENTITY_RESOLUTION')
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
    ) OR NOT EXISTS (
        SELECT 1 FROM ai_model_preference
        WHERE scope = 'SYSTEM' AND scope_id IS NULL
          AND capability = 'KNOWLEDGE_SUMMARY'
    ) THEN
        RAISE EXCEPTION 'v21 无法初始化知识抽取与摘要模型偏好：缺少已启用的 qwen:qwen3.8-flash 或 CHAT 模型';
    END IF;
END $$;

-- 平台向导知识库归属 admin 个人组织：租户范围与组织过滤要求 org_id 匹配，否则管理员无法在后台查看与维护。
CREATE TEMP TABLE seed_aaf_platform_guide_owner ON COMMIT DROP AS
SELECT admin_user.id AS owner_id, org.id AS org_id
FROM sys_user admin_user
JOIN sys_organization org
    ON org.owner_id = admin_user.id
    AND org.type = 'personal'
    AND org.deleted = FALSE
WHERE admin_user.username = 'admin'
  AND admin_user.deleted = FALSE
ORDER BY org.id
LIMIT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM seed_aaf_platform_guide_owner) THEN
        RAISE EXCEPTION 'v21 平台向导知识库缺少 admin 个人组织归属';
    END IF;
END $$;

-- 平台向导知识库（系统公开；由绑定该库的角色经 knowledge_search 工具按需检索，auto_inject 当前不参与运行时）。
-- 库内文档不在种子中预置：启动时 PlatformGuideDocumentInitializer 只将 bootstrap/ 下的框架介绍与产品服务介绍登记到文档库，
-- 由管理员在知识库页面"从文档库导入"后才切片、向量化与实体抽取。
INSERT INTO ai_knowledge_base (
    id, stable_id, org_id, visibility, scope_code, name, description, embedding_model,
    chunk_strategy, chunk_size, chunk_overlap, status, auto_inject,
    projection_watermark, owner_id, create_time, update_time, deleted)
SELECT
    1, '00000000-0000-0000-0000-000000000301', owner.org_id, 'SYSTEM_PUBLIC',
    'system:aaf-platform-guide', 'AAF 平台向导知识库',
    '产品咨询、常见问题和功能说明，供默认用户助理的平台向导 Role 使用',
    NULL, 'RECURSIVE', 512, 64, 0, FALSE,
    1, owner.owner_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM seed_aaf_platform_guide_owner owner
ON CONFLICT (id) DO NOTHING;

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
