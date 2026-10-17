-- ============================================================
-- AAF-120 #12004 profile：长者与联系人 EntityDef
-- 侧边栏分组与 v403 的 sys_menu「养老」分组保持一致。
-- ============================================================

-- ==================== 长者档案 ====================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'elder',
    $json$
    {
      "kind": "code",
      "resource": "elder.profile.elder",
      "label": "长者",
      "labelPlural": "长者",
      "icon": "heart-handshake",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"relationship","name":"user","label":"关联 AAF 账号","relationTo":"system.user","writeKey":"userId"},
        {"type":"text","name":"fullName","label":"姓名","required":true,"maxLength":100},
        {"type":"select","name":"gender","label":"性别","dictType":"sys_user_sex"},
        {"type":"date","name":"birthDate","label":"出生日期"},
        {"type":"text","name":"idCardNo","label":"身份证件号","maxLength":64},
        {"type":"text","name":"mobile","label":"联系电话","maxLength":32},
        {"type":"textarea","name":"address","label":"居住地址","maxLength":500,"rows":3},
        {"type":"select","name":"livingArrangement","label":"居住形态","required":true,"dictType":"elder_living_arrangement"},
        {"type":"text","name":"preferredName","label":"习惯称呼","maxLength":100},
        {"type":"textarea","name":"preferences","label":"生活偏好","rows":4},
        {"type":"textarea","name":"taboos","label":"沟通与照护禁忌","rows":4},
        {"type":"select","name":"status","label":"档案状态","required":true,"dictType":"elder_status"},
        {"type":"checkbox","name":"cameraConsent","label":"已同意摄像头与预警"},
        {"type":"relationship","name":"cameraConsentUser","label":"同意人","relationTo":"system.user","writeKey":"cameraConsentBy","visibleWhen":"$record.cameraConsent === true"},
        {"type":"date","name":"cameraConsentAt","label":"同意时间","includeTime":true,"visibleWhen":"$record.cameraConsent === true"},
        {"type":"subtable","name":"contacts","label":"联系人与授权","childEntity":"elder-contact","columns":["name","mobile","relationship","priority","emergencyContact","authorizationScopes"],"sortable":true,"readOnly":true},
        {"type":"subtable","name":"healthItems","label":"健康条目","childEntity":"health-item","columns":["itemType","name","occurredDate"],"readOnly":true},
        {"type":"subtable","name":"medicationPlans","label":"用药计划","childEntity":"medication-plan","columns":["medicationName","dosageInstructions","reminderTimes","startDate","endDate","status"],"readOnly":true},
        {"type":"subtable","name":"medicationRecords","label":"服药记录","childEntity":"medication-record","columns":["scheduledAt","result","source","recordedAt"],"readOnly":true}
      ],
      "actions": [],
      "listView": {
        "columns": ["preferredName","gender","livingArrangement","status","cameraConsent","createTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["fullName","preferredName"],
        "filterableFields": ["status","livingArrangement"],
        "quickFilters": [
          {"label":"启用档案","conditions":[{"field":"status","operator":"eq","values":["ACTIVE"]}]},
          {"label":"独居长者","conditions":[{"field":"livingArrangement","operator":"eq","values":["ALONE"]}]}
        ],
        "batchActions": ["delete"]
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

-- ==================== 长者联系人 ====================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'elder-contact',
    $json$
    {
      "kind": "code",
      "resource": "elder.profile.elder-contact",
      "label": "长者联系人",
      "labelPlural": "长者联系人",
      "icon": "contact-round",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","required":true},
        {"type":"relationship","name":"user","label":"关联 AAF 账号","relationTo":"system.user","writeKey":"userId"},
        {"type":"text","name":"name","label":"联系人姓名","required":true,"maxLength":100},
        {"type":"text","name":"mobile","label":"联系电话","required":true,"maxLength":32},
        {"type":"select","name":"relationship","label":"与长者关系","required":true,"dictType":"elder_contact_relationship"},
        {"type":"number","name":"priority","label":"联系优先级","required":true,"min":0,"description":"数值越小越优先"},
        {"type":"checkbox","name":"emergencyContact","label":"紧急联系人"},
        {"type":"select","name":"authorizationScopes","label":"授权范围","dictType":"elder_authorization_scope","multiple":true,"description":"绑定 AAF 账号后授权才生效"}
      ],
      "actions": [],
      "listView": {
        "columns": ["elder","relationship","priority","emergencyContact","createTime"],
        "defaultSort": "priority:asc",
        "searchableFields": ["name","mobile"],
        "filterableFields": ["relationship"],
        "quickFilters": [
          {"label":"子女","conditions":[{"field":"relationship","operator":"eq","values":["CHILD"]}]},
          {"label":"监护人","conditions":[{"field":"relationship","operator":"eq","values":["GUARDIAN"]}]}
        ],
        "batchActions": ["delete"]
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



-- ============================================================
-- AAF-120 #12005 profile：健康条目与用药 EntityDef
-- ============================================================

-- ==================== 健康条目 ====================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'health-item',
    $json$
    {
      "kind": "code",
      "resource": "elder.profile.health-item",
      "label": "健康条目",
      "labelPlural": "健康条目",
      "icon": "heart-pulse",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","required":true},
        {"type":"select","name":"itemType","label":"条目类型","required":true,"dictType":"elder_health_item_type"},
        {"type":"text","name":"name","label":"条目名称","required":true,"maxLength":200},
        {"type":"textarea","name":"description","label":"详细说明","rows":4},
        {"type":"date","name":"occurredDate","label":"发生或确诊日期"}
      ],
      "actions": [],
      "listView": {
        "columns": ["elder","itemType","name","occurredDate","createTime"],
        "defaultSort": "occurredDate:desc",
        "searchableFields": ["name"],
        "filterableFields": ["elderId","itemType"],
        "batchActions": ["delete"]
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

-- ==================== 用药计划 ====================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'medication-plan',
    $json$
    {
      "kind": "code",
      "resource": "elder.profile.medication-plan",
      "label": "用药计划",
      "labelPlural": "用药计划",
      "icon": "pill",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","required":true},
        {"type":"text","name":"medicationName","label":"药品名称","required":true,"maxLength":200,"description":"仅录入人提供的信息，系统不提供用药建议"},
        {"type":"textarea","name":"dosageInstructions","label":"剂量说明","required":true,"maxLength":500,"rows":3},
        {"type":"json","name":"reminderTimes","label":"每日提醒时间点","required":true,"description":"HH:mm 字符串数组，如 [\"08:00\",\"20:00\"]"},
        {"type":"date","name":"startDate","label":"生效日期","required":true},
        {"type":"date","name":"endDate","label":"结束日期"},
        {"type":"select","name":"status","label":"状态","required":true,"dictType":"elder_medication_plan_status"},
        {"type":"select","name":"source","label":"创建来源","dictType":"elder_medication_plan_source","readOnly":true},
        {"type":"date","name":"nextReminderAt","label":"下一提醒时间","readOnly":true,"includeTime":true}
      ],
      "actions": [
        {
          "key": "activate",
          "label": "启用",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/profile/medication-plans/actions/activate",
          "visibleWhen": "$record.status === 'INACTIVE'",
          "position": "rowAction"
        },
        {
          "key": "deactivate",
          "label": "停用",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/profile/medication-plans/actions/deactivate",
          "confirmMessage": "停用后不再生成新的用药提醒，是否继续？",
          "visibleWhen": "$record.status === 'ACTIVE'",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["elder","medicationName","dosageInstructions","reminderTimes","startDate","endDate","status","source"],
        "defaultSort": "id:desc",
        "searchableFields": ["medicationName"],
        "filterableFields": ["elderId","status","source"],
        "quickFilters": [
          {"label":"已启用","conditions":[{"field":"status","operator":"eq","values":["ACTIVE"]}]},
          {"label":"已停用","conditions":[{"field":"status","operator":"eq","values":["INACTIVE"]}]}
        ],
        "batchActions": ["delete"]
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

-- ==================== 服药记录（管理端只读） ====================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'medication-record',
    $json$
    {
      "kind": "code",
      "resource": "elder.profile.medication-record",
      "label": "服药记录",
      "labelPlural": "服药记录",
      "icon": "clipboard-check",
      "group": "elder",
      "groupLabel": "养老",
      "access": {"read":true,"create":false,"update":false,"delete":false},
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","readOnly":true},
        {"type":"relationship","name":"plan","label":"用药计划","relationTo":"elder.profile.medication-plan","writeKey":"planId","readOnly":true},
        {"type":"date","name":"scheduledAt","label":"计划服药时间","readOnly":true,"includeTime":true},
        {"type":"select","name":"result","label":"服药结果","dictType":"elder_medication_record_result","readOnly":true},
        {"type":"select","name":"source","label":"记录来源","dictType":"elder_medication_record_source","readOnly":true},
        {"type":"date","name":"recordedAt","label":"确认或判定时间","readOnly":true,"includeTime":true},
        {"type":"text","name":"sourceReference","label":"来源引用","readOnly":true}
      ],
      "actions": [],
      "listView": {
        "columns": ["elder","plan","scheduledAt","result","source","recordedAt"],
        "defaultSort": "scheduledAt:desc",
        "filterableFields": ["elderId","planId","result","source"]
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


-- ============================================================
-- AAF-120 #12018 rehab：康复指导 EntityDef
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'rehab-guide',
    $json$
    {
      "kind": "code",
      "resource": "elder.rehab.guide",
      "label": "康复指导",
      "labelPlural": "康复指导",
      "icon": "accessibility",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"text","name":"title","label":"标题","required":true,"maxLength":200},
        {"type":"select","name":"category","label":"分类","required":true,"dictType":"elder_rehab_guide_category"},
        {"type":"textarea","name":"summary","label":"摘要","required":true,"maxLength":1000,"rows":4},
        {"type":"textarea","name":"applicableAudience","label":"适用人群说明","required":true,"maxLength":1000,"rows":4},
        {"type":"relationship","name":"knowledgeDocument","label":"关联知识文档","relationTo":"knowledge.knowledge-document","writeKey":"knowledgeDocumentId","required":true},
        {"type":"select","name":"status","label":"状态","dictType":"elder_rehab_guide_status","readOnly":true}
      ],
      "actions": [
        {
          "key": "publish",
          "label": "发布",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/rehab/guides/actions/publish",
          "confirmMessage": "发布后该指导关联文档将进入康复问答检索范围，是否继续？",
          "visibleWhen": "$record.status === 'DRAFT'",
          "position": "rowAction"
        },
        {
          "key": "offline",
          "label": "下线",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/rehab/guides/actions/offline",
          "confirmMessage": "下线后关联文档将立即退出康复问答检索范围，是否继续？",
          "visibleWhen": "$record.status === 'PUBLISHED'",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["title","category","summary","knowledgeDocument","status","createTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["title"],
        "filterableFields": ["category","status","knowledgeDocumentId"],
        "quickFilters": [
          {"label":"已发布","conditions":[{"field":"status","operator":"eq","values":["PUBLISHED"]}]},
          {"label":"草稿","conditions":[{"field":"status","operator":"eq","values":["DRAFT"]}]}
        ],
        "batchActions": ["delete"]
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



-- ============================================================
-- AAF-120 #12010 guard：预警与处理记录 EntityDef
-- ============================================================

-- ==================== 预警事件 ====================
INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'elder-alert',
    $json$
    {
      "kind": "code",
      "resource": "elder.guard.alert",
      "label": "养老预警",
      "labelPlural": "养老预警",
      "icon": "triangle-alert",
      "group": "elder",
      "groupLabel": "养老",
      "access": {"read":true,"create":false,"update":true,"delete":false},
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","readOnly":true},
        {"type":"relationship","name":"device","label":"来源设备","relationTo":"device.device","writeKey":"deviceId","readOnly":true},
        {"type":"text","name":"eventId","label":"事件幂等键","readOnly":true},
        {"type":"select","name":"alertType","label":"预警类型","dictType":"elder_alert_type","readOnly":true},
        {"type":"select","name":"level","label":"预警级别","dictType":"elder_alert_level","readOnly":true},
        {"type":"select","name":"status","label":"处理结论","dictType":"elder_alert_status","description":"仅已知晓预警可更新为已处理或误报"},
        {"type":"select","name":"zone","label":"发生区域","dictType":"device_zone","readOnly":true},
        {"type":"number","name":"confidence","label":"边缘置信度","readOnly":true},
        {"type":"json","name":"payload","label":"语义载荷","readOnly":true},
        {"type":"date","name":"deviceOccurredAt","label":"设备发生时间","includeTime":true,"readOnly":true},
        {"type":"date","name":"receivedAt","label":"服务端接收时间","includeTime":true,"readOnly":true},
        {"type":"checkbox","name":"late","label":"晚到事件","readOnly":true},
        {"type":"relationship","name":"claimedByUser","label":"认领人","relationTo":"system.user","writeKey":"claimedBy","readOnly":true},
        {"type":"date","name":"claimedAt","label":"认领时间","includeTime":true,"readOnly":true},
        {"type":"date","name":"acknowledgedAt","label":"已知晓时间","includeTime":true,"readOnly":true},
        {"type":"date","name":"resolvedAt","label":"结案时间","includeTime":true,"readOnly":true},
        {"type":"textarea","name":"remark","label":"处理备注","rows":4,"maxLength":255,"description":"结案必填，并写明实际处理人"}
      ],
      "actions": [
        {
          "key": "claim",
          "label": "认领",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/guard/alerts/actions/claim",
          "visibleWhen": "$record.status === 'NOTIFYING' && !$record.claimedBy",
          "position": "rowAction"
        },
        {
          "key": "acknowledge",
          "label": "代记已知晓",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/guard/alerts/actions/acknowledge",
          "confirmMessage": "请确认已通过电话联系联系人，并代记对方已知晓该预警。",
          "visibleWhen": "$record.status === 'NOTIFYING' && !!$record.claimedBy",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["level","alertType","elder","zone","status","late","claimedByUser","deviceOccurredAt","receivedAt"],
        "filterableFields": ["elderId","deviceId","alertType","level","status","late","claimedBy"],
        "quickFilters": [
          {"label":"未结案","conditions":[{"field":"status","operator":"in","values":["TRIGGERED","NOTIFYING","ACKED"]}]},
          {"label":"未认领","conditions":[{"field":"claimedBy","operator":"isNull","values":[]}]},
          {"label":"紧急预警","conditions":[{"field":"level","operator":"eq","values":["EMERGENCY"]}]},
          {"label":"晚到事件","conditions":[{"field":"late","operator":"eq","values":["true"]}]}
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

-- ==================== 预警处理记录（管理端只读） ====================
INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'elder-alert-handling',
    $json$
    {
      "kind": "code",
      "resource": "elder.guard.alert-handling",
      "label": "预警处理记录",
      "labelPlural": "预警处理记录",
      "icon": "list-checks",
      "group": "elder",
      "groupLabel": "养老",
      "access": {"read":true,"create":false,"update":false,"delete":false},
      "fields": [
        {"type":"relationship","name":"alert","label":"预警","relationTo":"elder.guard.alert","writeKey":"alertId","readOnly":true},
        {"type":"select","name":"handlingType","label":"处理类型","dictType":"elder_alert_handling_type","readOnly":true},
        {"type":"select","name":"channel","label":"渠道","dictType":"elder_alert_handling_channel","readOnly":true},
        {"type":"select","name":"status","label":"执行状态","dictType":"elder_alert_handling_status","readOnly":true},
        {"type":"select","name":"recipientType","label":"接收方类型","dictType":"elder_alert_recipient_type","readOnly":true},
        {"type":"number","name":"recipientId","label":"接收方 ID","readOnly":true},
        {"type":"relationship","name":"operatorUser","label":"操作人","relationTo":"system.user","writeKey":"operatorUserId","readOnly":true},
        {"type":"relationship","name":"acknowledgedContact","label":"确认对象","relationTo":"elder.profile.elder-contact","writeKey":"acknowledgedContactId","readOnly":true},
        {"type":"textarea","name":"detail","label":"处理摘要","readOnly":true,"rows":3},
        {"type":"textarea","name":"errorMessage","label":"失败原因","readOnly":true,"rows":3},
        {"type":"date","name":"handledAt","label":"发生时间","includeTime":true,"readOnly":true}
      ],
      "actions": [],
      "listView": {
        "columns": ["alert","handlingType","channel","status","recipientType","operatorUser","acknowledgedContact","handledAt"],
        "defaultSort": "handledAt:desc",
        "filterableFields": ["alertId","handlingType","channel","status","operatorUserId","acknowledgedContactId"],
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

-- 康复指导关联选择器所需的知识文档只读引用定义；无菜单，写操作仍走知识库聚合入口。
INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'knowledge-document',
    $json$
    {
      "kind": "code",
      "resource": "knowledge.knowledge-document",
      "label": "知识文档",
      "labelPlural": "知识文档",
      "icon": "file-text",
      "group": "knowledge",
      "groupLabel": "知识库",
      "access": {"read":true,"create":false,"update":false,"delete":false},
      "fields": [
        {"type":"number","name":"knowledgeBaseId","label":"知识库 ID","readOnly":true},
        {"type":"text","name":"title","label":"文档标题","readOnly":true},
        {"type":"text","name":"sourceType","label":"来源类型","readOnly":true},
        {"type":"number","name":"status","label":"处理状态","readOnly":true}
      ],
      "actions": [],
      "listView": {
        "columns": ["title","knowledgeBaseId","sourceType","status","createTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["title"],
        "filterableFields": ["knowledgeBaseId","status"]
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


-- ============================================================
-- AAF-120 #12014 album：相册 EntityDef
-- upload 是 EntityDef 已支持的图片预览字段；地址指向需用户鉴权的相册内容端点。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'elder-photo',
    $json$
    {
      "kind": "code",
      "resource": "elder.album.photo",
      "label": "长者照片",
      "labelPlural": "长者照片",
      "icon": "images",
      "group": "elder",
      "groupLabel": "养老",
      "access": {"read":true,"create":false,"update":true,"delete":true},
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","readOnly":true},
        {"type":"number","name":"deviceId","label":"拍摄设备 ID","readOnly":true},
        {"type":"upload","name":"thumbnailUrl","label":"缩略图/预览","accept":"image/jpeg,image/png,image/webp","readOnly":true},
        {"type":"upload","name":"originalUrl","label":"原图预览","accept":"image/jpeg,image/png,image/webp","readOnly":true},
        {"type":"select","name":"source","label":"拍摄来源","dictType":"elder_photo_source","readOnly":true},
        {"type":"date","name":"capturedAt","label":"拍摄时间","includeTime":true,"readOnly":true},
        {"type":"textarea","name":"description","label":"照片说明","maxLength":500,"rows":3},
        {"type":"text","name":"originalName","label":"原始文件名","readOnly":true},
        {"type":"text","name":"mimeType","label":"文件格式","readOnly":true},
        {"type":"number","name":"fileSize","label":"文件大小（字节）","readOnly":true}
      ],
      "actions": [],
      "listView": {
        "columns": ["thumbnailUrl","elder","deviceId","source","capturedAt","description"],
        "defaultSort": "capturedAt:desc",
        "searchableFields": ["description","originalName"],
        "filterableFields": ["elderId","deviceId","source"],
        "batchActions": ["delete"]
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


-- ============================================================
-- AAF-120 #12015 mall：服务项目与预约 EntityDef
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'service-item',
    $json$
    {
      "kind": "code",
      "resource": "elder.mall.service-item",
      "label": "服务项目",
      "labelPlural": "服务项目",
      "icon": "shopping-bag",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"text","name":"name","label":"项目名称","required":true,"maxLength":200},
        {"type":"textarea","name":"description","label":"项目说明","required":true,"maxLength":2000,"rows":4},
        {"type":"select","name":"category","label":"服务分类","required":true,"dictType":"elder_service_category"},
        {"type":"text","name":"unit","label":"服务单位","required":true,"maxLength":32},
        {"type":"text","name":"priceDescription","label":"价格说明","required":true,"maxLength":200,"description":"一期线下结算"},
        {"type":"number","name":"durationMinutes","label":"服务时长（分钟）","required":true,"min":1},
        {"type":"select","name":"status","label":"状态","dictType":"elder_service_item_status","readOnly":true}
      ],
      "actions": [
        {
          "key": "publish",
          "label": "上架",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/mall/service-items/actions/publish",
          "visibleWhen": "$record.status === 'UNAVAILABLE'",
          "position": "rowAction"
        },
        {
          "key": "offline",
          "label": "下架",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/mall/service-items/actions/offline",
          "confirmMessage": "下架后不能创建新的服务预约，是否继续？",
          "visibleWhen": "$record.status === 'AVAILABLE'",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["name","category","unit","priceDescription","durationMinutes","status","createTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["name"],
        "filterableFields": ["category","status"],
        "quickFilters": [
          {"label":"已上架","conditions":[{"field":"status","operator":"eq","values":["AVAILABLE"]}]},
          {"label":"已下架","conditions":[{"field":"status","operator":"eq","values":["UNAVAILABLE"]}]}
        ],
        "batchActions": ["delete"]
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

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'service-order',
    $json$
    {
      "kind": "code",
      "resource": "elder.mall.service-order",
      "label": "服务预约",
      "labelPlural": "服务预约",
      "icon": "calendar-check",
      "group": "elder",
      "groupLabel": "养老",
      "fields": [
        {"type":"relationship","name":"elder","label":"长者","relationTo":"elder.profile.elder","writeKey":"elderId","required":true},
        {"type":"relationship","name":"serviceItem","label":"服务项目","relationTo":"elder.mall.service-item","writeKey":"serviceItemId","required":true},
        {"type":"number","name":"quantity","label":"数量","required":true,"min":1},
        {"type":"date","name":"scheduledAt","label":"预约服务时间","required":true,"includeTime":true},
        {"type":"textarea","name":"serviceAddress","label":"服务地址","required":true,"maxLength":500,"rows":3},
        {"type":"textarea","name":"notes","label":"预约备注","maxLength":1000,"rows":3},
        {"type":"select","name":"status","label":"预约状态","dictType":"elder_service_order_status","readOnly":true},
        {"type":"date","name":"confirmedAt","label":"确认时间","includeTime":true,"readOnly":true},
        {"type":"date","name":"completedAt","label":"完成时间","includeTime":true,"readOnly":true},
        {"type":"date","name":"cancelledAt","label":"取消时间","includeTime":true,"readOnly":true}
      ],
      "actions": [
        {
          "key": "confirm",
          "label": "确认",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/mall/service-orders/actions/confirm",
          "confirmMessage": "确认后将向长者机器人下发服务当天提醒，是否继续？",
          "visibleWhen": "$record.status === 'CREATED'",
          "position": "rowAction"
        },
        {
          "key": "cancel",
          "label": "取消",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/mall/service-orders/actions/cancel",
          "confirmMessage": "取消已确认预约时会同步撤销机器人提醒，是否继续？",
          "visibleWhen": "$record.status === 'CREATED' || $record.status === 'CONFIRMED'",
          "position": "rowAction"
        },
        {
          "key": "complete",
          "label": "标记完成",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/elder/mall/service-orders/actions/complete",
          "confirmMessage": "确认该服务已经线下完成？",
          "visibleWhen": "$record.status === 'CONFIRMED'",
          "position": "rowAction"
        }
      ],
      "listView": {
        "columns": ["elder","serviceItem","quantity","scheduledAt","serviceAddress","status","confirmedAt"],
        "defaultSort": "scheduledAt:desc",
        "filterableFields": ["elderId","serviceItemId","status"],
        "quickFilters": [
          {"label":"待确认","conditions":[{"field":"status","operator":"eq","values":["CREATED"]}]},
          {"label":"已确认","conditions":[{"field":"status","operator":"eq","values":["CONFIRMED"]}]}
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
