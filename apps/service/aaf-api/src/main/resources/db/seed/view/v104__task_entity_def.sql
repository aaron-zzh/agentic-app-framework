-- ============================================================
-- 任务管理内置 EntityDef
-- 资源的 L1/L3 授权由后端 CRUD 管线执行；本配置仅提供只读 UI 元数据。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('async-task', $json$
    {
      "kind": "code",
      "resource": "system.async-task",
      "label": "异步任务",
      "labelPlural": "异步任务",
      "icon": "list-restart",
      "group": "system",
      "groupLabel": "系统管理",
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"记录 ID","readOnly":true},
        {"type":"text","name":"taskId","label":"任务 ID","readOnly":true},
        {"type":"text","name":"taskType","label":"任务类型","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"number","name":"priority","label":"优先级","readOnly":true},
        {"type":"number","name":"attemptCount","label":"已尝试次数","readOnly":true},
        {"type":"number","name":"maxRetries","label":"最大重试次数","readOnly":true},
        {"type":"text","name":"result","label":"结果摘要","readOnly":true},
        {"type":"text","name":"lastError","label":"最后错误","readOnly":true},
        {"type":"number","name":"ownerId","label":"提交者 ID","readOnly":true},
        {"type":"number","name":"orgId","label":"组织 ID","readOnly":true},
        {"type":"number","name":"workspaceId","label":"工作区 ID","readOnly":true},
        {"type":"date","name":"startedAt","label":"开始时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"completedAt","label":"完成时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["taskId","taskType","status","attemptCount","maxRetries","startedAt","completedAt","createTime"],
        "defaultSort": "createTime:desc",
        "searchableFields": ["taskId","taskType"],
        "filterableFields": ["status","taskType"]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('task-execution', $json$
    {
      "kind": "code",
      "resource": "system.task-execution",
      "label": "任务执行审计",
      "labelPlural": "任务执行审计",
      "icon": "scroll-text",
      "group": "system",
      "groupLabel": "系统管理",
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"记录 ID","readOnly":true},
        {"type":"text","name":"taskName","label":"任务名称","readOnly":true},
        {"type":"text","name":"taskType","label":"任务类型","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"date","name":"startTime","label":"开始时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"endTime","label":"结束时间","readOnly":true,"includeTime":true},
        {"type":"number","name":"durationMs","label":"耗时（毫秒）","readOnly":true},
        {"type":"text","name":"errorMessage","label":"错误信息","readOnly":true},
        {"type":"number","name":"retryCount","label":"重试次数","readOnly":true},
        {"type":"number","name":"priority","label":"优先级","readOnly":true},
        {"type":"text","name":"triggerType","label":"触发类型","readOnly":true},
        {"type":"text","name":"bizId","label":"业务标识","readOnly":true},
        {"type":"text","name":"context","label":"执行上下文","readOnly":true},
        {"type":"number","name":"orgId","label":"组织 ID","readOnly":true},
        {"type":"number","name":"workspaceId","label":"工作区 ID","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["taskName","taskType","status","startTime","endTime","durationMs","retryCount","priority","bizId"],
        "defaultSort": "startTime:desc",
        "searchableFields": ["taskName","taskType","bizId"],
        "filterableFields": ["status","taskType"]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE)
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
