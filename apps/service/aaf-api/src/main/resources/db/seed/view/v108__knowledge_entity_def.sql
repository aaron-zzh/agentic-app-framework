-- 知识库内置 EntityDef（后台运维）
-- 复用现有代码资源 knowledge.knowledge-base；accessMode 由通用 CRUD 客户端转为固定治理 Header。
-- 运维看板（统计、投影 checkpoint、文档处理）通过 customViews 的记录级自定义视图挂在实体下。
-- 资源操作与字段权限以后端 CRUD catalog 为准；本配置仅提供 UI 元数据。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'knowledge-base',
    $json$
    {
      "kind": "code",
      "resource": "knowledge.knowledge-base",
      "accessMode": "admin-maintenance",
      "label": "知识库运维",
      "labelPlural": "知识库运维",
      "icon": "database",
      "group": "knowledge",
      "groupLabel": "知识库",
      "description": "知识库运维：统计、向量与图投影重建、失败文档重试",
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"text","name":"name","label":"名称","readOnly":true},
        {"type":"textarea","name":"description","label":"描述","readOnly":true,"rows":3},
        {"type":"select","name":"visibility","label":"可见性","readOnly":true,
          "options":[
            {"label":"私有","value":"PRIVATE"},
            {"label":"组织","value":"ORG"},
            {"label":"系统公共","value":"SYSTEM_PUBLIC"}
          ]},
        {"type":"text","name":"scopeCode","label":"授权范围编码","readOnly":true},
        {"type":"text","name":"embeddingModel","label":"Embedding 模型","readOnly":true},
        {"type":"text","name":"chunkStrategy","label":"分块策略","readOnly":true},
        {"type":"number","name":"chunkSize","label":"分块大小","readOnly":true},
        {"type":"number","name":"chunkOverlap","label":"分块重叠","readOnly":true},
        {"type":"number","name":"documentCount","label":"文档数","readOnly":true},
        {"type":"number","name":"status","label":"状态","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["name","visibility","scopeCode","documentCount","status","updateTime"],
        "defaultSort": "updateTime:desc",
        "searchableFields": ["name"],
        "filterableFields": ["visibility","status"]
      },
      "customViews": [
        {
          "key": "ops",
          "label": "运维",
          "icon": "wrench",
          "component": "knowledge-ops",
          "scope": "record"
        }
      ]
    }
    $json$::jsonb,
    TRUE,
    TRUE
)
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
