-- ============================================================
-- AIGC 内置 EntityDef
-- AIGC 任务保持 L3 个人隔离。
-- ============================================================
INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('aigc-task', $json$
    {
      "kind": "code",
      "resource": "aigc.aigc-task",
      "label": "AIGC 任务",
      "labelPlural": "AIGC 任务",
      "icon": "wand-2",
      "group": "aigc",
      "groupLabel": "AI 创作",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": true
      },
      "fields": [
        {
          "type": "number",
          "name": "id",
          "label": "任务 ID",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "userId",
          "label": "用户 ID",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "type",
          "label": "任务类型",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "status",
          "label": "状态",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "provider",
          "label": "提供商",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "model",
          "label": "模型",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "prompt",
          "label": "提示词",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "resultUrl",
          "label": "结果地址",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "ossUrl",
          "label": "存储地址",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "errorMsg",
          "label": "失败原因",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "projectId",
          "label": "项目 ID",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "createTime",
          "label": "创建时间",
          "readOnly": true,
          "includeTime": true
        }
      ],
      "listView": {
        "columns": [
          "id",
          "type",
          "status",
          "provider",
          "model",
          "projectId",
          "createTime"
        ],
        "defaultSort": "id:desc",
        "batchActions": [
          "delete"
        ]
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE)
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
