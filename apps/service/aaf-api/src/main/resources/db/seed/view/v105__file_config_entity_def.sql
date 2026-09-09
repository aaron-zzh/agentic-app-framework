-- ============================================================
-- 文件存储配置内置 EntityDef
-- 资源操作与字段权限以后端 CRUD catalog 为准；本配置仅提供 UI 元数据。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES (
    'file-config',
    $json$
    {
      "kind": "code",
      "resource": "system.file-config",
      "label": "文件存储配置",
      "labelPlural": "文件存储配置",
      "icon": "database",
      "group": "system",
      "groupLabel": "系统管理",
      "fields": [
        {"type":"number","name":"id","label":"ID","readOnly":true},
        {"type":"number","name":"version","label":"版本号","readOnly":true},
        {"type":"text","name":"name","label":"名称","required":true},
        {"type":"select","name":"storageType","label":"存储类型","required":true,
          "options":[
            {"label":"本地存储","value":"LOCAL"},
            {"label":"S3 兼容存储","value":"S3"},
            {"label":"阿里云 OSS","value":"OSS"}
          ]},
        {"type":"json","name":"config","label":"存储配置","required":true},
        {"type":"text","name":"credentialRef","label":"凭证引用","readOnly":true},
        {"type":"checkbox","name":"credentialConfigured","label":"凭证已配置","readOnly":true},
        {"type":"checkbox","name":"master","label":"主配置","readOnly":true},
        {"type":"date","name":"createTime","label":"创建时间","readOnly":true,"includeTime":true},
        {"type":"date","name":"updateTime","label":"更新时间","readOnly":true,"includeTime":true}
      ],
      "listView": {
        "columns": ["name","storageType","credentialConfigured","master","updateTime"],
        "defaultSort": "id:desc",
        "searchableFields": ["name"],
        "filterableFields": ["storageType","master"]
      },
      "actions": [
        {
          "key": "set-master",
          "label": "设为主配置",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/system/file-configs/actions/set-master",
          "confirmMessage": "系统将先验证存储读写能力，验证通过后切换全局上传主存储，是否继续？",
          "visibleWhen": "$record.master !== true",
          "position": "rowAction"
        },
        {
          "key": "validate",
          "label": "验证配置",
          "type": "single",
          "execution": "sync",
          "endpoint": "/api/system/file-configs/actions/validate",
          "position": "rowAction"
        }
      ],
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
