-- ============================================================
-- 通用设备层 module/device EntityDef
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'device',
    $json$
    {
      "kind": "code",
      "resource": "device.device",
      "label": "设备",
      "labelPlural": "设备",
      "icon": "bot",
      "group": "device",
      "groupLabel": "设备管理",
      "access": {"read":true,"create":true,"update":true,"delete":false},
      "fields": [
        {"type":"text","name":"pairCode","label":"配对码","required":true,"minLength":8,"maxLength":8,"displayModes":["create"],"description":"输入设备屏幕显示的 8 位配对码"},
        {"type":"select","name":"scenarioKey","label":"设备场景","required":true,"dictType":"device_scenario","displayModes":["create","edit"],"description":"决定设备做什么：通用与办公/伴学只需设备所有者；养老需要先选择长者"},
        {"type":"recordReference","name":"subject","label":"场景主体","writeKey":"subject","idValueType":"number","displayModes":["create","edit"],"description":"需要主体的场景（如养老的长者）必须选择，其他场景请留空；没有对应档案时请先到相应业务模块新建，切换到不需要主体的场景前请先清空"},
        {"type":"text","name":"roleKey","label":"角色覆盖","maxLength":128,"displayModes":["create","edit"],"description":"可选，填写已绑定到所有者默认助理的角色编码"},
        {"type":"relationship","name":"ownerUser","label":"设备所有者","relationTo":"system.user","writeKey":"ownerUserId","required":true,"displayModes":["create"],"description":"语音、对话和合成的计费主体"},
        {"type":"text","name":"name","label":"设备名称","required":true,"maxLength":100},
        {"type":"select","name":"deviceType","label":"设备类型","required":true,"dictType":"device_type"},
        {"type":"select","name":"zone","label":"部署区域","required":true,"dictType":"device_zone"},
        {"type":"text","name":"serialNumber","label":"硬件序列号","readOnly":true},
        {"type":"text","name":"deviceKey","label":"设备键","readOnly":true},
        {"type":"text","name":"model","label":"型号","readOnly":true},
        {"type":"text","name":"firmwareVersion","label":"固件版本","readOnly":true},
        {"type":"text","name":"credentialPrefix","label":"凭据前缀","readOnly":true},
        {"type":"select","name":"status","label":"绑定状态","dictType":"device_status","readOnly":true},
        {"type":"select","name":"onlineStatus","label":"在线状态","dictType":"device_online_status","readOnly":true},
        {"type":"select","name":"cameraStatus","label":"摄像头状态","dictType":"device_camera_status","readOnly":true},
        {"type":"checkbox","name":"privacyMode","label":"隐私模式","readOnly":true},
        {"type":"date","name":"lastHeartbeatAt","label":"最近心跳","includeTime":true,"readOnly":true},
        {"type":"date","name":"lastPersonSeenAt","label":"最近发现人员","includeTime":true,"readOnly":true},
        {"type":"date","name":"credentialLastUsedAt","label":"凭据最近使用","includeTime":true,"readOnly":true},
        {"type":"date","name":"credentialRevokedAt","label":"凭据撤销时间","includeTime":true,"readOnly":true}
      ],
      "actions": [
        {
          "key": "unbind",
          "label": "解绑",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/device/devices/actions/unbind",
          "confirmMessage": "解绑将立即撤销设备凭据并断开 MQTT 连接，设备会回到配对界面。是否继续？",
          "visibleWhen": "$record.status === 'BOUND'",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["name","serialNumber","scenarioKey","subject","ownerUser","deviceType","zone","status","onlineStatus","firmwareVersion","lastHeartbeatAt"],
        "defaultSort": "id:desc",
        "searchableFields": ["name","serialNumber"],
        "filterableFields": ["status","onlineStatus","deviceType","scenarioKey","ownerUserId"],
        "quickFilters": [
          {"label":"已绑定","conditions":[{"field":"status","operator":"eq","values":["BOUND"]}]},
          {"label":"离线设备","conditions":[{"field":"onlineStatus","operator":"eq","values":["OFFLINE"]}]}
        ],
        "batchActions": []
      },
      "mixins": ["baseEntity"]
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
