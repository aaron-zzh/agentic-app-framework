-- ============================================================
-- 数字人模块 EntityDef
-- 定义数字人和预设音色的管理视图
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('digital-human', $json$
    {
      "kind": "code",
      "resource": "digital-human.digital-human",
      "label": "数字人",
      "labelPlural": "数字人",
      "icon": "user-circle",
      "group": "digital-human",
      "groupLabel": "数字人",
      "access": {
        "read": true,
        "create": true,
        "update": true,
        "delete": true
      },
      "fields": [
        {"type": "number", "name": "id", "label": "ID", "readOnly": true},
        {"type": "number", "name": "version", "label": "版本号", "readOnly": true},
        {"type": "text", "name": "name", "label": "名称", "required": true},
        {"type": "text", "name": "description", "label": "描述"},
        {"type": "array", "name": "tags", "label": "标签"},
        {"type": "text", "name": "avatarImageUrl", "label": "形象URL", "required": true},
        {"type": "json", "name": "appearance", "label": "外观配置"},
        {"type": "json", "name": "voiceConfig", "label": "音色配置", "required": true},
        {"type": "json", "name": "modelConfig", "label": "模型配置"},
        {"type": "number", "name": "assistantId", "label": "关联助理ID"},
        {"type": "checkbox", "name": "syncModelConfig", "label": "同步模型配置"},
        {"type": "number", "name": "videoCount", "label": "视频数量", "readOnly": true},
        {"type": "number", "name": "totalDuration", "label": "总时长(秒)", "readOnly": true},
        {"type": "number", "name": "creditsConsumed", "label": "消耗积分", "readOnly": true},
        {"type": "date", "name": "lastUsedAt", "label": "最后使用时间", "readOnly": true, "includeTime": true},
        {"type": "text", "name": "status", "label": "状态", "readOnly": true},
        {"type": "number", "name": "userId", "label": "用户ID", "readOnly": true},
        {"type": "number", "name": "orgId", "label": "组织ID", "readOnly": true},
        {"type": "number", "name": "workspaceId", "label": "工作空间ID", "readOnly": true},
        {"type": "date", "name": "createTime", "label": "创建时间", "readOnly": true, "includeTime": true},
        {"type": "date", "name": "updateTime", "label": "更新时间", "readOnly": true, "includeTime": true}
      ],
      "listView": {
        "columns": ["id", "name", "tags", "status", "videoCount", "creditsConsumed", "lastUsedAt", "updateTime"],
        "defaultSort": "updateTime:desc",
        "filters": [
          {"field": "name", "operator": "contains", "label": "名称"},
          {"field": "status", "operator": "eq", "label": "状态"},
          {"field": "tags", "operator": "contains", "label": "标签"}
        ],
        "batchActions": ["delete"]
      },
      "detailView": {
        "sections": [
          {
            "title": "基础信息",
            "fields": ["name", "description", "tags", "status"]
          },
          {
            "title": "形象配置",
            "fields": ["avatarImageUrl", "appearance"]
          },
          {
            "title": "音色配置",
            "fields": ["voiceConfig"]
          },
          {
            "title": "模型配置",
            "fields": ["modelConfig", "assistantId", "syncModelConfig"]
          },
          {
            "title": "使用统计",
            "fields": ["videoCount", "totalDuration", "creditsConsumed", "lastUsedAt"]
          },
          {
            "title": "系统信息",
            "fields": ["id", "version", "userId", "orgId", "workspaceId", "createTime", "updateTime"]
          }
        ]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),

    ('preset-voice', $json$
    {
      "kind": "code",
      "resource": "digital-human.preset-voice",
      "label": "预设音色",
      "labelPlural": "预设音色",
      "icon": "mic",
      "group": "digital-human",
      "groupLabel": "数字人",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {"type": "number", "name": "id", "label": "ID", "readOnly": true},
        {"type": "text", "name": "provider", "label": "供应商", "readOnly": true},
        {"type": "text", "name": "voiceId", "label": "音色ID", "readOnly": true},
        {"type": "text", "name": "voiceName", "label": "音色名称", "readOnly": true},
        {"type": "text", "name": "gender", "label": "性别", "readOnly": true},
        {"type": "text", "name": "ageRange", "label": "年龄范围", "readOnly": true},
        {"type": "text", "name": "style", "label": "风格", "readOnly": true},
        {"type": "text", "name": "description", "label": "描述", "readOnly": true},
        {"type": "text", "name": "previewAudioUrl", "label": "试听URL", "readOnly": true},
        {"type": "checkbox", "name": "enabled", "label": "启用", "readOnly": true},
        {"type": "number", "name": "sortOrder", "label": "排序", "readOnly": true}
      ],
      "listView": {
        "columns": ["id", "voiceName", "gender", "ageRange", "style", "description", "enabled"],
        "defaultSort": "sortOrder:asc",
        "filters": [
          {"field": "provider", "operator": "eq", "label": "供应商"},
          {"field": "gender", "operator": "eq", "label": "性别"},
          {"field": "enabled", "operator": "eq", "label": "启用状态"}
        ]
      },
      "detailView": {
        "sections": [
          {
            "title": "音色信息",
            "fields": ["provider", "voiceId", "voiceName", "gender", "ageRange", "style", "description"]
          },
          {
            "title": "其他",
            "fields": ["previewAudioUrl", "enabled", "sortOrder"]
          }
        ]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE);
