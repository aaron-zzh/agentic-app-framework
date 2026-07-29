-- ============================================================
-- 分销内置 EntityDef
-- 分销管理权限与菜单授权见 v14。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('brokerage-user', $json$
    {
      "kind": "code",
      "resource": "brokerage.brokerage-user",
      "label": "分销员",
      "labelPlural": "分销员",
      "icon": "users",
      "group": "brokerage",
      "groupLabel": "分销",
      "access": {
        "read": true,
        "create": false,
        "update": true,
        "delete": false
      },
      "fields": [
        {
          "type": "number",
          "name": "contactId",
          "label": "联系人 ID",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "referrerContactId",
          "label": "推荐人联系人 ID",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "referrerBindTime",
          "label": "推荐绑定时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "checkbox",
          "name": "brokerageEnabled",
          "label": "分销资格"
        },
        {
          "type": "date",
          "name": "brokerageTime",
          "label": "成为分销员时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "number",
          "name": "balance",
          "label": "可用佣金（分）",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "frozen",
          "label": "冻结佣金（分）",
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
          "contactId",
          "referrerContactId",
          "brokerageEnabled",
          "balance",
          "frozen",
          "createTime"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('brokerage-record', $json$
    {
      "kind": "code",
      "resource": "brokerage.brokerage-record",
      "label": "佣金流水",
      "labelPlural": "佣金流水",
      "icon": "receipt",
      "group": "brokerage",
      "groupLabel": "分销",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "number",
          "name": "contactId",
          "label": "分销员联系人 ID",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "sourceContactId",
          "label": "来源联系人 ID",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "sourceLevel",
          "label": "推广层级",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "bizType",
          "label": "业务类型",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "bizId",
          "label": "业务 ID",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "title",
          "label": "标题",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "amount",
          "label": "佣金金额（分）",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "status",
          "label": "状态",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "frozenDays",
          "label": "冻结天数",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "unfreezeTime",
          "label": "解冻时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "number",
          "name": "ruleId",
          "label": "规则 ID",
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
          "contactId",
          "sourceLevel",
          "bizType",
          "title",
          "amount",
          "status",
          "unfreezeTime",
          "createTime"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('brokerage-withdraw', $json$
    {
      "kind": "code",
      "resource": "brokerage.brokerage-withdraw",
      "label": "佣金提现",
      "labelPlural": "佣金提现",
      "icon": "banknote",
      "group": "brokerage",
      "groupLabel": "分销",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "number",
          "name": "contactId",
          "label": "申请人联系人 ID",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "amount",
          "label": "提现金额（分）",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "fee",
          "label": "手续费（分）",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "type",
          "label": "提现类型",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "accountName",
          "label": "收款人",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "accountNo",
          "label": "收款账号",
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
          "name": "auditReason",
          "label": "审核原因",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "auditTime",
          "label": "审核时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "number",
          "name": "payTransferId",
          "label": "转账单 ID",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "transferTime",
          "label": "转账时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "date",
          "name": "createTime",
          "label": "申请时间",
          "readOnly": true,
          "includeTime": true
        }
      ],
      "listView": {
        "columns": [
          "contactId",
          "amount",
          "fee",
          "type",
          "status",
          "auditTime",
          "transferTime",
          "createTime"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('brokerage-rule', $json$
    {
      "kind": "code",
      "resource": "brokerage.brokerage-rule",
      "label": "佣金规则",
      "labelPlural": "佣金规则",
      "icon": "percent",
      "group": "brokerage",
      "groupLabel": "分销",
      "fields": [
        {
          "type": "text",
          "name": "name",
          "label": "规则名称",
          "required": true
        },
        {
          "type": "text",
          "name": "bizType",
          "label": "业务类型",
          "required": true,
          "readOnlyWhen": "id != null"
        },
        {
          "type": "text",
          "name": "bizTargetType",
          "label": "目标类型"
        },
        {
          "type": "text",
          "name": "bizTargetId",
          "label": "目标 ID"
        },
        {
          "type": "number",
          "name": "level1Rate",
          "label": "一级佣金比例",
          "required": true
        },
        {
          "type": "number",
          "name": "level2Rate",
          "label": "二级佣金比例",
          "required": true
        },
        {
          "type": "text",
          "name": "calcBase",
          "label": "计算基准"
        },
        {
          "type": "number",
          "name": "fixedAmount",
          "label": "固定佣金（分）"
        },
        {
          "type": "number",
          "name": "frozenDays",
          "label": "冻结天数"
        },
        {
          "type": "number",
          "name": "priority",
          "label": "优先级"
        },
        {
          "type": "text",
          "name": "status",
          "label": "状态"
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
          "name",
          "bizType",
          "level1Rate",
          "level2Rate",
          "frozenDays",
          "priority",
          "status"
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
    $json$::jsonb, TRUE, TRUE),
    ('brokerage-level-bonus', $json$
    {
      "kind": "code",
      "resource": "brokerage.brokerage-level-bonus",
      "label": "等级佣金加成",
      "labelPlural": "等级佣金加成",
      "icon": "badge-percent",
      "group": "brokerage",
      "groupLabel": "分销",
      "fields": [
        {
          "type": "number",
          "name": "ruleId",
          "label": "佣金规则 ID",
          "required": true,
          "readOnlyWhen": "id != null"
        },
        {
          "type": "number",
          "name": "planId",
          "label": "会员套餐 ID",
          "required": true,
          "readOnlyWhen": "id != null"
        },
        {
          "type": "number",
          "name": "level1Rate",
          "label": "一级佣金比例",
          "required": true
        },
        {
          "type": "number",
          "name": "level2Rate",
          "label": "二级佣金比例",
          "required": true
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
          "ruleId",
          "planId",
          "level1Rate",
          "level2Rate",
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
