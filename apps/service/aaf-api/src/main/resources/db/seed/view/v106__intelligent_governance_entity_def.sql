-- ============================================================
-- Prompt / Skill 智能治理 EntityDef
-- 复用现有代码资源；accessMode 由通用 CRUD 客户端转为固定治理 Header。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('prompt-template', $json$
    {
      "kind": "code",
      "resource": "ai.prompt-template",
      "accessMode": "admin-maintenance",
      "label": "Prompt 治理",
      "labelPlural": "Prompt 治理",
      "icon": "message-square-code",
      "group": "intelligent-governance",
      "groupLabel": "智能治理",
      "description": "统一治理 TEMPLATE、ENGINE、PROCESSING 与 POLICY Prompt",
      "access": {"read": true, "create": false, "update": true, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"text","name":"code","label":"稳定代码","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"select","name":"kind","label":"种类","readOnly":true,
          "options":[
            {"label":"模板","value":"TEMPLATE"},
            {"label":"引擎","value":"ENGINE"},
            {"label":"处理","value":"PROCESSING"},
            {"label":"策略","value":"POLICY"}
          ]},
        {"type":"text","name":"type","label":"类型"},
        {"type":"text","name":"visibility","label":"可见性","readOnly":true},
        {"type":"text","name":"scope","label":"使用场景"},
        {"type":"json","name":"categories","label":"分类 code"},
        {"type":"textarea","name":"description","label":"说明","rows":3},
        {"type":"number","name":"version","label":"当前发布版本","readOnly":true},
        {"type":"number","name":"draftVersion","label":"Draft 版本","readOnly":true},
        {"type":"text","name":"draftStatus","label":"Draft 状态","readOnly":true},
        {"type":"textarea","name":"prompt","label":"Prompt 内容","rows":16,
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"textarea","name":"negativePrompt","label":"负向 Prompt","rows":6,
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"text","name":"model","label":"模型",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"number","name":"width","label":"宽度",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"number","name":"height","label":"高度",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"number","name":"steps","label":"步数",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"number","name":"seed","label":"随机种子",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"json","name":"variables","label":"变量",
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"textarea","name":"changeSummary","label":"变更说明","rows":3,
          "readOnlyWhen":"$record.draftStatus !== 'DRAFT'"},
        {"type":"number","name":"ownerId","label":"归属用户","readOnly":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["code","name","kind","type","visibility","version","draftVersion","draftStatus","updateTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["code","name"],
        "filterableFields": ["kind","type","visibility","scope"],
        "serverPagination": true
      },
      "actions": [
        {
          "key": "create-draft",
          "label": "创建 Draft",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/ai/prompts/actions/create-draft",
          "confirmMessage": "将从当前发布版本创建 Draft，是否继续？",
          "visibleWhen": "$record.draftStatus !== 'DRAFT'",
          "position": "rowAction"
        },
        {
          "key": "publish-draft",
          "label": "发布 Draft",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/ai/prompts/actions/publish-draft",
          "confirmMessage": "发布后将切换运行时当前版本，是否继续？",
          "visibleWhen": "$record.draftStatus === 'DRAFT'",
          "position": "rowAction"
        }
      ],
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('skill', $json$
    {
      "kind": "code",
      "resource": "ai.skill",
      "accessMode": "admin-maintenance",
      "label": "Skill 治理",
      "labelPlural": "Skill 治理",
      "icon": "brain-circuit",
      "group": "intelligent-governance",
      "groupLabel": "智能治理",
      "description": "治理 Skill 根对象、不可变版本与发布状态",
      "access": {"read": true, "create": false, "update": true, "delete": false},
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"根版本","readOnly":true},
        {"type":"text","name":"code","label":"稳定代码","required":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"textarea","name":"summary","label":"摘要","required":true,"rows":3},
        {"type":"textarea","name":"instancePrompt","label":"实例输入提示词","rows":4},
        {"type":"text","name":"locale","label":"语言地区"},
        {"type":"select","name":"visibility","label":"可见性",
          "options":[
            {"label":"私有","value":"PRIVATE"},
            {"label":"工作区","value":"WORKSPACE"},
            {"label":"公开","value":"PUBLIC"}
          ]},
        {"type":"checkbox","name":"builtIn","label":"内置","readOnly":true},
        {"type":"number","name":"currentVersionId","label":"当前版本 ID","readOnly":true},
        {"type":"number","name":"sourceSkillId","label":"来源 Skill ID"},
        {"type":"json","name":"categoryCodes","label":"分类 code"},
        {"type":"textarea","name":"content","label":"新版本 Markdown","rows":16},
        {"type":"textarea","name":"inputSchema","label":"输入 Schema","rows":8},
        {"type":"textarea","name":"outputSchema","label":"输出 Schema","rows":8},
        {"type":"textarea","name":"outputContract","label":"输出契约","rows":6},
        {"type":"select","name":"toolAccessMode","label":"工具访问模式",
          "options":[
            {"label":"限制","value":"RESTRICT"},
            {"label":"继承","value":"INHERIT"}
          ]},
        {"type":"json","name":"toolRequirements","label":"工具需求"},
        {"type":"json","name":"modelRequirements","label":"模型需求"},
        {"type":"textarea","name":"changeSummary","label":"版本变更说明","rows":3},
        {"type":"select","name":"status","label":"新版本状态",
          "options":[
            {"label":"草稿","value":"DRAFT"},
            {"label":"审核中","value":"IN_REVIEW"},
            {"label":"已批准","value":"APPROVED"},
            {"label":"已拒绝","value":"REJECTED"},
            {"label":"已退役","value":"RETIRED"}
          ]},
        {"type":"number","name":"ownerId","label":"归属用户","readOnly":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["code","name","summary","locale","visibility","builtIn","currentVersionId","updateTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["code","name","summary"],
        "filterableFields": ["locale","visibility","builtIn"],
        "serverPagination": true
      },
      "actions": [
        {
          "key": "publish-latest",
          "label": "发布最新 APPROVED 版本",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/system/skills/actions/publish-latest",
          "confirmMessage": "将最新 APPROVED 版本设为 current，是否继续？",
          "position": "rowAction"
        }
      ],
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE)
ON CONFLICT (slug) WHERE deleted = FALSE DO UPDATE
SET config = EXCLUDED.config,
    builtin = EXCLUDED.builtin,
    enabled = EXCLUDED.enabled,
    update_time = CURRENT_TIMESTAMP;
