-- ============================================================
-- 数字人模块 EntityDef
-- 定义数字人和预设音色的管理视图
-- 字段必须来自资源的展示 VO（DigitalHumanVO / PresetVoiceVO），否则 EntityDefService 会拒绝
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
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"textarea","name":"description","label":"描述","rows":3},
        {"type":"json","name":"tags","label":"标签"},
        {"type":"text","name":"avatarImageUrl","label":"形象 URL","required":true},
        {"type":"json","name":"appearance","label":"外观配置"},
        {"type":"json","name":"voiceConfig","label":"音色配置","required":true},
        {"type":"json","name":"modelConfig","label":"模型配置"},
        {"type":"number","name":"assistantId","label":"关联助理 ID","readOnly":true},
        {"type":"checkbox","name":"syncModelConfig","label":"同步模型配置"},
        {"type":"number","name":"videoCount","label":"视频数量","readOnly":true},
        {"type":"number","name":"totalDuration","label":"总时长（秒）","readOnly":true},
        {"type":"number","name":"creditsConsumed","label":"消耗积分","readOnly":true},
        {"type":"date","name":"lastUsedAt","label":"最后使用时间","readOnly":true,"includeTime":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"text","name":"generationSource","label":"生成来源","readOnly":true},
        {"type":"number","name":"aigcProjectId","label":"AIGC 项目 ID","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["id","name","status","videoCount","creditsConsumed","lastUsedAt","updateTime"],
        "defaultSort": "updateTime:desc",
        "searchableFields": ["name"],
        "filterableFields": ["status"],
        "batchActions": ["delete"]
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
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"text","name":"name","label":"名称","readOnly":true},
        {"type":"text","name":"description","label":"描述","readOnly":true},
        {"type":"text","name":"gender","label":"性别","readOnly":true},
        {"type":"text","name":"sampleAudioUrl","label":"试听 URL","readOnly":true},
        {"type":"text","name":"provider","label":"供应商","readOnly":true},
        {"type":"text","name":"externalVoiceId","label":"外部音色 ID","readOnly":true},
        {"type":"checkbox","name":"isActive","label":"启用","readOnly":true},
        {"type":"number","name":"displayOrder","label":"显示顺序","readOnly":true}
      ],
      "listView": {
        "columns": ["id","name","gender","provider","description","isActive"],
        "defaultSort": "displayOrder:asc",
        "searchableFields": ["name"],
        "filterableFields": ["provider","gender","isActive"]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE);
