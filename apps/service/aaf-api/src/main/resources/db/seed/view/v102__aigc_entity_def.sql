-- ============================================================
-- AIGC 内置 EntityDef
-- 仅描述当前面向管理视图的 BaseCrud 根；业务命令由专用 API 承担。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('brand-profile', $json$
    {
      "kind": "code", "resource": "aigc.brand-profile",
      "label": "品牌/IP 资料", "labelPlural": "品牌/IP 资料", "icon": "badge-check",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"kind","label":"类型","required":true},
        {"type":"text","name":"industry","label":"行业"},
        {"type":"number","name":"currentVersionId","label":"当前资料版本 ID","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","kind","industry","status","updateTime"],"defaultSort":"updateTime:desc","batchActions":["delete"]},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('project-type', $json$
    {
      "kind": "code", "resource": "aigc.project-type",
      "label": "项目类型", "labelPlural": "项目类型", "icon": "shapes",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"code","label":"编码","required":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"icon","label":"图标"},
        {"type":"text","name":"description","label":"描述"},
        {"type":"text","name":"briefPlaceholder","label":"简报占位提示"},
        {"type":"text","name":"definitionVersion","label":"定义版本","required":true},
        {"type":"json","name":"defaultChannels","label":"默认渠道"},
        {"type":"text","name":"defaultProductionMode","label":"默认生产模式"},
        {"type":"checkbox","name":"quickEntry","label":"快捷入口"},
        {"type":"checkbox","name":"builtin","label":"内置","readOnly":true},
        {"type":"number","name":"sortOrder","label":"排序"},
        {"type":"text","name":"status","label":"状态","readOnly":true}
      ],
      "listView": {"columns":["code","name","definitionVersion","defaultProductionMode","quickEntry","status"],"defaultSort":"sortOrder:asc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('project-type-package', $json$
    {
      "kind": "code", "resource": "aigc.project-type-package",
      "label": "项目类型兼容包", "labelPlural": "项目类型兼容包", "icon": "package-check",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"packageVersion","label":"包版本","required":true},
        {"type":"number","name":"projectTypeId","label":"项目类型 ID","required":true},
        {"type":"number","name":"blueprintId","label":"蓝图 ID","required":true},
        {"type":"number","name":"domainExtensionId","label":"领域扩展 ID"},
        {"type":"json","name":"channelSpecIds","label":"渠道规格 ID"},
        {"type":"json","name":"executionBindingIds","label":"执行绑定 ID"},
        {"type":"text","name":"productionMode","label":"生产模式","required":true},
        {"type":"json","name":"compatibilityResult","label":"兼容性结果","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true}
      ],
      "listView": {"columns":["packageVersion","projectTypeId","blueprintId","productionMode","status"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('blueprint', $json$
    {
      "kind": "code", "resource": "aigc.blueprint",
      "label": "项目蓝图", "labelPlural": "项目蓝图", "icon": "network",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"code","label":"编码","required":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"projectTypeCode","label":"项目类型编码","required":true},
        {"type":"text","name":"blueprintVersion","label":"蓝图版本","required":true},
        {"type":"text","name":"productionMode","label":"生产模式","required":true},
        {"type":"text","name":"description","label":"描述"},
        {"type":"text","name":"coverUrl","label":"封面 URL"},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"json","name":"slotTemplateSpec","label":"槽位模板规格"},
        {"type":"json","name":"relationSpec","label":"关系规格"},
        {"type":"json","name":"actionSpec","label":"动作规格"},
        {"type":"json","name":"deliverableSpec","label":"交付物规格"},
        {"type":"json","name":"processPolicy","label":"流程策略"},
        {"type":"json","name":"briefFields","label":"简报字段"}
      ],
      "listView": {"columns":["code","name","projectTypeCode","blueprintVersion","productionMode","status"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('domain-extension', $json$
    {
      "kind": "code", "resource": "aigc.domain-extension",
      "label": "领域扩展", "labelPlural": "领域扩展", "icon": "blocks",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"code","label":"编码","required":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"extensionVersion","label":"扩展版本","required":true},
        {"type":"text","name":"industry","label":"行业"},
        {"type":"text","name":"region","label":"地区"},
        {"type":"text","name":"language","label":"语言"},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"json","name":"profileSchemaExt","label":"资料 Schema 扩展"},
        {"type":"json","name":"objectDefinitions","label":"对象定义"},
        {"type":"json","name":"knowledgeRequirements","label":"知识要求"},
        {"type":"json","name":"ruleSets","label":"规则集"},
        {"type":"json","name":"validators","label":"校验器"},
        {"type":"json","name":"roleRecommendations","label":"角色推荐"},
        {"type":"json","name":"actionConstraints","label":"动作约束"},
        {"type":"json","name":"channelOverrides","label":"渠道覆盖"},
        {"type":"json","name":"migrationDeclaration","label":"迁移声明"}
      ],
      "listView": {"columns":["code","name","extensionVersion","industry","region","language","status"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('channel-spec', $json$
    {
      "kind": "code", "resource": "aigc.channel-spec",
      "label": "渠道规格", "labelPlural": "渠道规格", "icon": "send",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"code","label":"编码","required":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"specVersion","label":"规格版本","required":true},
        {"type":"text","name":"aspectRatio","label":"宽高比"},
        {"type":"number","name":"width","label":"宽度"},
        {"type":"number","name":"height","label":"高度"},
        {"type":"number","name":"maxDurationSeconds","label":"最大时长（秒）"},
        {"type":"json","name":"copyStructure","label":"文案结构"},
        {"type":"text","name":"requiredDisclaimers","label":"必需免责声明"},
        {"type":"text","name":"exportFormat","label":"导出格式"},
        {"type":"number","name":"sortOrder","label":"排序"},
        {"type":"text","name":"status","label":"状态","readOnly":true}
      ],
      "listView": {"columns":["code","name","specVersion","aspectRatio","width","height","status"],"defaultSort":"sortOrder:asc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('project', $json$
    {
      "kind": "code", "resource": "aigc.project",
      "label": "创作项目", "labelPlural": "创作项目", "icon": "folder-kanban",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": false, "update": true, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称"},
        {"type":"text","name":"description","label":"描述"},
        {"type":"text","name":"projectTypeCode","label":"项目类型"},
        {"type":"text","name":"blueprintCode","label":"蓝图编码","readOnly":true},
        {"type":"text","name":"blueprintVersion","label":"蓝图版本","readOnly":true},
        {"type":"text","name":"domainExtensionCode","label":"领域扩展编码","readOnly":true},
        {"type":"text","name":"domainExtensionVersion","label":"领域扩展版本","readOnly":true},
        {"type":"text","name":"productionMode","label":"生产模式"},
        {"type":"text","name":"generationMode","label":"生成模式"},
        {"type":"text","name":"status","label":"状态"},
        {"type":"text","name":"brief","label":"简报"},
        {"type":"text","name":"prompt","label":"提示词"},
        {"type":"number","name":"coverMediaVersionId","label":"封面媒体版本 ID"},
        {"type":"number","name":"configSnapshotId","label":"配置快照 ID","readOnly":true},
        {"type":"number","name":"graphRevision","label":"图谱修订号","readOnly":true},
        {"type":"number","name":"primaryBrandProfileId","label":"主品牌资料 ID"},
        {"type":"number","name":"assistantId","label":"助手 ID"},
        {"type":"number","name":"budgetLimit","label":"预算上限"},
        {"type":"number","name":"costUsed","label":"已用成本","readOnly":true},
        {"type":"date","name":"lastActiveTime","label":"最后活跃时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","projectTypeCode","productionMode","generationMode","status","costUsed","lastActiveTime"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('execution-run', $json$
    {
      "kind": "code", "resource": "aigc.execution-run",
      "label": "执行记录", "labelPlural": "执行记录", "icon": "activity",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"projectId","label":"项目 ID","readOnly":true},
        {"type":"number","name":"objectId","label":"对象 ID","readOnly":true},
        {"type":"number","name":"parentExecutionRunId","label":"父执行 ID","readOnly":true},
        {"type":"text","name":"actionKey","label":"动作键","readOnly":true},
        {"type":"text","name":"targetType","label":"目标类型","readOnly":true},
        {"type":"text","name":"targetRef","label":"目标引用","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"text","name":"generationMode","label":"生成模式","readOnly":true},
        {"type":"text","name":"roleProfileCode","label":"角色配置编码","readOnly":true},
        {"type":"text","name":"selectedModelVersion","label":"模型版本","readOnly":true},
        {"type":"json","name":"outputPayload","label":"输出载荷","readOnly":true},
        {"type":"json","name":"taskIds","label":"任务 ID","readOnly":true},
        {"type":"number","name":"costCredits","label":"积分成本","readOnly":true},
        {"type":"number","name":"retryCount","label":"重试次数","readOnly":true},
        {"type":"text","name":"errorMessage","label":"错误信息","readOnly":true},
        {"type":"date","name":"startTime","label":"开始时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"endTime","label":"结束时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","projectId","objectId","actionKey","targetType","status","costCredits","createTime"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('execution-binding', $json$
    {
      "kind": "code", "resource": "aigc.execution-binding",
      "label": "执行绑定", "labelPlural": "执行绑定", "icon": "route",
      "group": "aigc", "groupLabel": "AI 创作配置",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"actionKey","label":"动作键","required":true},
        {"type":"text","name":"projectTypeCode","label":"项目类型编码"},
        {"type":"text","name":"domainExtensionCode","label":"领域扩展编码"},
        {"type":"text","name":"productionMode","label":"生产模式"},
        {"type":"text","name":"channelCode","label":"渠道编码"},
        {"type":"text","name":"targetType","label":"目标类型","required":true},
        {"type":"text","name":"targetRef","label":"目标引用","required":true},
        {"type":"text","name":"bindingVersion","label":"绑定版本","required":true},
        {"type":"number","name":"priority","label":"优先级"},
        {"type":"checkbox","name":"confirmationRequired","label":"需要确认"},
        {"type":"number","name":"estimatedCredits","label":"预估积分"},
        {"type":"text","name":"status","label":"状态","readOnly":true}
      ],
      "listView": {"columns":["actionKey","projectTypeCode","productionMode","channelCode","targetType","targetRef","priority","status"],"defaultSort":"actionKey:asc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('task', $json$
    {
      "kind": "code", "resource": "aigc.task",
      "label": "AIGC 任务", "labelPlural": "AIGC 任务", "icon": "wand-2",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": false, "update": false, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"任务 ID","readOnly":true},
        {"type":"number","name":"userId","label":"用户 ID","readOnly":true},
        {"type":"text","name":"type","label":"任务类型","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"text","name":"provider","label":"提供商","readOnly":true},
        {"type":"text","name":"model","label":"模型","readOnly":true},
        {"type":"text","name":"prompt","label":"提示词","readOnly":true},
        {"type":"text","name":"providerTaskId","label":"供应商任务 ID","readOnly":true},
        {"type":"text","name":"providerResult","label":"供应商响应","readOnly":true},
        {"type":"number","name":"outputMediaId","label":"输出媒体 ID","readOnly":true},
        {"type":"number","name":"outputMediaVersionId","label":"输出媒体版本 ID","readOnly":true},
        {"type":"text","name":"outputUrl","label":"输出地址","readOnly":true},
        {"type":"number","name":"assetId","label":"资产 ID","readOnly":true},
        {"type":"checkbox","name":"isAsset","label":"已保存资产","readOnly":true},
        {"type":"text","name":"errorMsg","label":"失败原因","readOnly":true},
        {"type":"text","name":"params","label":"生成参数","readOnly":true},
        {"type":"number","name":"projectId","label":"项目 ID","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","type","status","provider","model","projectId","createTime"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('media', $json$
    {
      "kind": "code", "resource": "aigc.media",
      "label": "素材", "labelPlural": "素材", "icon": "image",
      "group": "aigc", "groupLabel": "资产管理",
      "access": {"read": true, "create": false, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"text","name":"name","label":"名称"},
        {"type":"text","name":"mediaType","label":"媒体类型","readOnly":true},
        {"type":"text","name":"sourceType","label":"来源类型","readOnly":true},
        {"type":"number","name":"sourceExecutionRunId","label":"来源执行 ID","readOnly":true},
        {"type":"number","name":"sourceTaskId","label":"来源任务 ID","readOnly":true},
        {"type":"number","name":"originalProjectId","label":"原始项目 ID","readOnly":true},
        {"type":"number","name":"assetId","label":"资产 ID","readOnly":true},
        {"type":"json","name":"currentVersion","label":"当前媒体版本","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","mediaType","sourceType","assetId","createTime"],"defaultSort":"createTime:desc","batchActions":["delete"]},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('asset', $json$
    {
      "kind": "code", "resource": "aigc.asset",
      "label": "资产", "labelPlural": "资产", "icon": "library",
      "group": "aigc", "groupLabel": "资产管理",
      "access": {"read": true, "create": false, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"mediaId","label":"媒体 ID","readOnly":true},
        {"type":"number","name":"categoryId","label":"分类 ID"},
        {"type":"text","name":"scope","label":"范围"},
        {"type":"text","name":"copyrightInfo","label":"版权信息"},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"number","name":"usageCount","label":"使用次数","readOnly":true},
        {"type":"json","name":"media","label":"媒体投影","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","mediaId","categoryId","scope","status","usageCount","createTime"],"defaultSort":"createTime:desc","batchActions":["delete"]},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('asset-category', $json$
    {
      "kind": "code", "resource": "aigc.asset-category",
      "label": "资产分类", "labelPlural": "资产分类", "icon": "folder-tree",
      "group": "aigc", "groupLabel": "资产管理",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"number","name":"parentId","label":"父分类 ID"},
        {"type":"number","name":"sortOrder","label":"排序"},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","parentId","sortOrder","updateTime"],"defaultSort":"sortOrder:asc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('asset-tag', $json$
    {
      "kind": "code", "resource": "aigc.asset-tag",
      "label": "资产标签", "labelPlural": "资产标签", "icon": "tags",
      "group": "aigc", "groupLabel": "资产管理",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"color","label":"颜色"},
        {"type":"number","name":"usageCount","label":"使用次数","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","color","usageCount","updateTime"],"defaultSort":"name:asc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('asset-collection', $json$
    {
      "kind": "code", "resource": "aigc.asset-collection",
      "label": "资产集合", "labelPlural": "资产集合", "icon": "layout-grid",
      "group": "aigc", "groupLabel": "资产管理",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"collectionType","label":"集合类型"},
        {"type":"text","name":"description","label":"描述"},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","name","collectionType","description","updateTime"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('work', $json$
    {
      "kind": "code", "resource": "aigc.work",
      "label": "作品", "labelPlural": "作品", "icon": "gallery-horizontal-end",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": false, "update": true, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"number","name":"projectId","label":"项目 ID","readOnly":true},
        {"type":"number","name":"deliverableSetObjectId","label":"交付集合对象 ID","readOnly":true},
        {"type":"number","name":"manifestObjectVersionId","label":"清单对象版本 ID","readOnly":true},
        {"type":"text","name":"title","label":"标题"},
        {"type":"number","name":"coverMediaVersionId","label":"封面媒体版本 ID"},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"text","name":"visibility","label":"可见范围"},
        {"type":"number","name":"userId","label":"用户 ID","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","projectId","title","status","visibility","userId","updateTime"],"defaultSort":"id:desc"},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('timeline', $json$
    {
      "kind": "code", "resource": "aigc.timeline",
      "label": "时间线", "labelPlural": "时间线", "icon": "list-video",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": false, "update": false, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"number","name":"projectId","label":"项目 ID","readOnly":true},
        {"type":"number","name":"deliverableObjectId","label":"交付对象 ID","readOnly":true},
        {"type":"text","name":"title","label":"标题","readOnly":true},
        {"type":"number","name":"durationMs","label":"时长（毫秒）","readOnly":true},
        {"type":"number","name":"fps","label":"帧率","readOnly":true},
        {"type":"number","name":"width","label":"宽度","readOnly":true},
        {"type":"number","name":"height","label":"高度","readOnly":true},
        {"type":"text","name":"status","label":"状态","readOnly":true},
        {"type":"number","name":"adoptedRevisionNo","label":"采用修订号","readOnly":true},
        {"type":"json","name":"tracks","label":"轨道","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {"columns":["id","projectId","deliverableObjectId","title","durationMs","fps","status","updateTime"],"defaultSort":"id:desc","batchActions":["delete"]},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('snippet', $json$
    {
      "kind": "code", "resource": "aigc.snippet",
      "label": "创作片段", "labelPlural": "创作片段", "icon": "text-quote",
      "group": "aigc", "groupLabel": "AI 创作",
      "access": {"read": true, "create": true, "update": true, "delete": true},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"text","name":"category","label":"分类"},
        {"type":"text","name":"content","label":"内容","required":true},
        {"type":"json","name":"referenceMediaVersionIds","label":"引用媒体版本 ID"},
        {"type":"json","name":"variableSlots","label":"变量槽位"},
        {"type":"text","name":"projectTypeCode","label":"项目类型编码"},
        {"type":"number","name":"brandProfileId","label":"品牌资料 ID"},
        {"type":"number","name":"useCount","label":"使用次数","readOnly":true},
        {"type":"checkbox","name":"isPublic","label":"公开"}
      ],
      "listView": {"columns":["id","name","category","projectTypeCode","brandProfileId","useCount","isPublic"],"defaultSort":"id:desc","batchActions":["delete"]},
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE)
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
