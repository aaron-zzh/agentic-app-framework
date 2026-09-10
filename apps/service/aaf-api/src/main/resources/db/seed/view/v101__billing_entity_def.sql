-- ============================================================
-- Billing 内置 EntityDef
-- 所有管理视图使用 BaseCrud 资源；购买、兑换等业务流程由同资源专用 action 承担。
-- ============================================================

INSERT INTO sys_entity_def (slug, config, builtin, enabled)
VALUES
    ('level', $json$
    {
      "kind": "code",
      "resource": "billing.level",
      "label": "会员等级",
      "labelPlural": "会员等级",
      "icon": "crown",
      "group": "billing",
      "groupLabel": "会员中心",
      "fields": [
        {
          "type": "text",
          "name": "code",
          "label": "等级编码",
          "required": true
        },
        {
          "type": "text",
          "name": "name",
          "label": "等级名称",
          "required": true
        },
        {
          "type": "number",
          "name": "expMin",
          "label": "最低成长值",
          "required": true
        },
        {
          "type": "number",
          "name": "expMax",
          "label": "最高成长值",
          "required": true
        },
        {
          "type": "json",
          "name": "perks",
          "label": "等级权益"
        },
        {
          "type": "number",
          "name": "sort",
          "label": "排序"
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
          "code",
          "name",
          "expMin",
          "expMax",
          "sort",
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
    $json$::jsonb, TRUE, TRUE),
    ('subscription-plan', $json$
    {
      "kind": "code",
      "resource": "billing.subscription-plan",
      "label": "订阅套餐",
      "labelPlural": "订阅套餐",
      "icon": "credit-card",
      "group": "billing",
      "groupLabel": "会员中心",
      "fields": [
        {
          "type": "text",
          "name": "code",
          "label": "套餐编码",
          "required": true
        },
        {
          "type": "text",
          "name": "name",
          "label": "套餐名称",
          "required": true
        },
        {
          "type": "number",
          "name": "monthlyCredits",
          "label": "每月积分"
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "options": [
            {
              "label": "启用",
              "value": "ENABLED",
              "color": "green"
            },
            {
              "label": "停用",
              "value": "DISABLED",
              "color": "gray"
            }
          ]
        },
        {
          "type": "number",
          "name": "sort",
          "label": "排序"
        },
        {
          "type": "json",
          "name": "ext",
          "label": "扩展配置"
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
          "code",
          "name",
          "monthlyCredits",
          "status",
          "sort"
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
    ('subscription-sku', $json$
    {
      "kind": "code",
      "resource": "billing.subscription-sku",
      "label": "订阅 SKU",
      "labelPlural": "订阅 SKU",
      "icon": "badge-dollar-sign",
      "group": "billing",
      "groupLabel": "会员中心",
      "fields": [
        {
          "type": "relationship",
          "name": "plan",
          "label": "套餐",
          "relationTo": "billing.subscription-plan",
          "writeKey": "planId",
          "required": true
        },
        {
          "type": "text",
          "name": "skuCode",
          "label": "SKU 编码",
          "required": true
        },
        {
          "type": "select",
          "name": "billingCycle",
          "label": "计费周期",
          "required": true,
          "options": [
            {"label": "永久", "value": "PERPETUAL"},
            {"label": "月付", "value": "MONTH"},
            {"label": "季付", "value": "QUARTER"},
            {"label": "年付", "value": "YEAR"}
          ]
        },
        {
          "type": "number",
          "name": "cycleMonths",
          "label": "自然月数",
          "required": true
        },
        {
          "type": "number",
          "name": "price",
          "label": "售价（分）",
          "required": true
        },
        {
          "type": "number",
          "name": "marketPrice",
          "label": "市场价（分）",
          "required": true
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "options": [
            {"label": "启用", "value": "ENABLED", "color": "green"},
            {"label": "停用", "value": "DISABLED", "color": "gray"}
          ]
        },
        {"type": "number", "name": "sort", "label": "排序"},
        {"type": "json", "name": "ext", "label": "扩展配置"},
        {
          "type": "date",
          "name": "createTime",
          "label": "创建时间",
          "readOnly": true,
          "includeTime": true
        }
      ],
      "listView": {
        "columns": ["plan", "skuCode", "billingCycle", "cycleMonths", "price", "marketPrice", "status", "sort"],
        "defaultSort": "id:desc",
        "searchableFields": ["skuCode"]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('subscription', $json$
    {
      "kind": "code",
      "resource": "billing.subscription",
      "label": "用户订阅",
      "labelPlural": "用户订阅",
      "icon": "zap",
      "group": "billing",
      "groupLabel": "会员中心",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "relationship",
          "name": "user",
          "label": "用户",
          "relationTo": "system.user",
          "readOnly": true
        },
        {
          "type": "relationship",
          "name": "plan",
          "label": "套餐",
          "relationTo": "billing.subscription-plan",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "planCode",
          "label": "套餐编码",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "skuCode",
          "label": "SKU 编码",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "billingCycle",
          "label": "订阅周期",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "startAt",
          "label": "开始时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "date",
          "name": "endAt",
          "label": "到期时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "readOnly": true,
          "options": [
            {
              "label": "生效中",
              "value": "ACTIVE",
              "color": "green"
            },
            {
              "label": "已过期",
              "value": "EXPIRED",
              "color": "gray"
            },
            {
              "label": "已取消",
              "value": "CANCELLED",
              "color": "red"
            }
          ]
        },
        {
          "type": "date",
          "name": "cancelledAt",
          "label": "取消时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "text",
          "name": "pendingSkuCode",
          "label": "待生效 SKU",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "pendingBillingCycle",
          "label": "待生效周期",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "pendingPlanName",
          "label": "待生效套餐",
          "readOnly": true
        }
      ],
      "listView": {
        "columns": [
          "user",
          "plan",
          "skuCode",
          "pendingSkuCode",
          "startAt",
          "endAt",
          "status"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('subscription-record', $json$
    {
      "kind": "code",
      "resource": "billing.subscription-record",
      "label": "订阅购买流水",
      "labelPlural": "订阅购买流水",
      "icon": "receipt-text",
      "group": "billing",
      "groupLabel": "会员中心",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {"type": "relationship", "name": "user", "label": "用户", "relationTo": "system.user", "readOnly": true},
        {"type": "relationship", "name": "plan", "label": "套餐", "relationTo": "billing.subscription-plan", "readOnly": true},
        {"type": "relationship", "name": "sku", "label": "SKU", "relationTo": "billing.subscription-sku", "readOnly": true},
        {"type": "text", "name": "operation", "label": "操作", "readOnly": true},
        {"type": "number", "name": "payOrderId", "label": "支付单 ID", "readOnly": true},
        {"type": "number", "name": "skuPriceSnapshot", "label": "SKU 名义价格快照", "readOnly": true},
        {"type": "number", "name": "payPrice", "label": "实付金额", "readOnly": true},
        {"type": "text", "name": "payStatus", "label": "支付状态", "readOnly": true},
        {"type": "date", "name": "payTime", "label": "支付时间", "readOnly": true, "includeTime": true},
        {"type": "text", "name": "fulfillmentStatus", "label": "履约状态", "readOnly": true},
        {"type": "number", "name": "checkoutGenerationId", "label": "下单世代", "readOnly": true},
        {"type": "date", "name": "checkoutCalculatedAt", "label": "下单计算时间", "readOnly": true, "includeTime": true},
        {"type": "json", "name": "checkoutValueSnapshot", "label": "下单价值快照", "readOnly": true},
        {"type": "number", "name": "serviceGenerationId", "label": "服务世代", "readOnly": true},
        {"type": "date", "name": "serviceStartAt", "label": "服务段开始", "readOnly": true, "includeTime": true},
        {"type": "date", "name": "serviceEndAt", "label": "服务段结束", "readOnly": true, "includeTime": true},
        {"type": "text", "name": "valueStatus", "label": "价值状态", "readOnly": true},
        {"type": "number", "name": "supersededByRecordId", "label": "替代流水 ID", "readOnly": true},
        {"type": "date", "name": "supersededAt", "label": "价值失效时间", "readOnly": true, "includeTime": true},
        {"type": "text", "name": "exceptionCode", "label": "异常码", "readOnly": true},
        {"type": "text", "name": "exceptionReason", "label": "异常原因", "readOnly": true},
        {"type": "date", "name": "exceptionDetectedAt", "label": "异常检测时间", "readOnly": true, "includeTime": true},
        {"type": "date", "name": "compensationResolvedAt", "label": "人工处理时间", "readOnly": true, "includeTime": true},
        {"type": "text", "name": "compensationResult", "label": "人工处理结果", "readOnly": true},
        {"type": "date", "name": "createTime", "label": "创建时间", "readOnly": true, "includeTime": true}
      ],
      "actions": [
        {
          "key": "resolveCompensation",
          "label": "记录人工处理结果",
          "type": "record",
          "execution": "sync",
          "endpoint": "/api/billing/subscription-records/{id}/actions/resolve-compensation",
          "confirmMessage": "仅记录人工处理事实，不会自动退款或履约。是否继续？",
          "position": "detail"
        }
      ],
      "listView": {
        "columns": ["user", "plan", "sku", "operation", "payPrice", "payStatus", "fulfillmentStatus", "exceptionCode", "exceptionDetectedAt", "compensationResolvedAt"],
        "defaultSort": "id:desc",
        "quickFilters": [
          {
            "label": "待人工补偿",
            "conditions": [{"field": "fulfillmentStatus", "operator": "eq", "values": ["COMPENSATION_PENDING"]}]
          }
        ]
      },
      "mixins": ["baseEntity"]
    }
    $json$::jsonb, TRUE, TRUE),
    ('entitlement-quota', $json$
    {
      "kind": "code",
      "resource": "billing.entitlement-quota",
      "label": "权益额度",
      "labelPlural": "权益额度",
      "icon": "wallet",
      "group": "billing",
      "groupLabel": "会员中心",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "relationship",
          "name": "user",
          "label": "用户",
          "relationTo": "system.user",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "code",
          "label": "权益编码",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "name",
          "label": "权益名称",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "total",
          "label": "总额度",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "used",
          "label": "已用",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "remain",
          "label": "剩余",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "nextResetAt",
          "label": "下次重置时间",
          "readOnly": true,
          "includeTime": true
        }
      ],
      "listView": {
        "columns": [
          "user",
          "code",
          "name",
          "total",
          "used",
          "remain",
          "nextResetAt"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('wallet-transaction', $json$
    {
      "kind": "code",
      "resource": "billing.wallet-transaction",
      "label": "积分流水",
      "labelPlural": "积分流水",
      "icon": "receipt",
      "group": "billing",
      "groupLabel": "会员中心",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "relationship",
          "name": "user",
          "label": "用户",
          "relationTo": "system.user",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "accountId",
          "label": "账户 ID",
          "readOnly": true
        },
        {
          "type": "select",
          "name": "type",
          "label": "类型",
          "readOnly": true,
          "options": [
            {
              "label": "获取",
              "value": "EARN",
              "color": "green"
            },
            {
              "label": "消费",
              "value": "SPEND",
              "color": "red"
            },
            {
              "label": "冻结",
              "value": "FREEZE",
              "color": "orange"
            },
            {
              "label": "解冻",
              "value": "UNFREEZE",
              "color": "blue"
            },
            {
              "label": "过期",
              "value": "EXPIRE",
              "color": "gray"
            }
          ]
        },
        {
          "type": "number",
          "name": "amount",
          "label": "变动金额",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "balanceAfter",
          "label": "变动后余额",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "source",
          "label": "来源",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "category",
          "label": "分类",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "batchType",
          "label": "积分批次",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "expireAt",
          "label": "过期时间",
          "readOnly": true,
          "includeTime": true
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
          "user",
          "type",
          "amount",
          "balanceAfter",
          "source",
          "batchType",
          "expireAt",
          "createTime"
        ],
        "defaultSort": "id:desc"
      },
      "mixins": [
        "baseEntity"
      ]
    }
    $json$::jsonb, TRUE, TRUE),
    ('credit-redeem-code', $json$
    {
      "kind": "code",
      "resource": "billing.credit-redeem-code",
      "label": "积分兑换码",
      "labelPlural": "积分兑换码",
      "icon": "ticket",
      "group": "billing",
      "groupLabel": "会员中心",
      "access": {
        "read": true,
        "create": false,
        "update": false,
        "delete": false
      },
      "fields": [
        {
          "type": "text",
          "name": "codePrefix",
          "label": "兑换码",
          "readOnly": true
        },
        {
          "type": "number",
          "name": "creditAmount",
          "label": "积分数量",
          "readOnly": true
        },
        {
          "type": "select",
          "name": "batchType",
          "label": "积分类型",
          "readOnly": true,
          "options": [
            {
              "label": "会员积分",
              "value": "SUBSCRIPTION",
              "color": "blue"
            },
            {
              "label": "购买积分",
              "value": "TOPUP",
              "color": "green"
            },
            {
              "label": "奖励积分",
              "value": "REWARD",
              "color": "orange"
            },
            {
              "label": "每周积分",
              "value": "WEEKLY",
              "color": "purple"
            },
            {
              "label": "额外赠送",
              "value": "MANUAL",
              "color": "gray"
            }
          ]
        },
        {
          "type": "select",
          "name": "type",
          "label": "兑换类型",
          "readOnly": true,
          "options": [
            {
              "label": "积分",
              "value": "CREDIT"
            },
            {
              "label": "会员",
              "value": "MEMBERSHIP"
            }
          ]
        },
        {
          "type": "relationship",
          "name": "sku",
          "label": "会员 SKU",
          "relationTo": "billing.subscription-sku",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "skuCode",
          "label": "SKU 编码",
          "readOnly": true
        },
        {
          "type": "text",
          "name": "billingCycle",
          "label": "计费周期",
          "readOnly": true
        },
        {
          "type": "relationship",
          "name": "plan",
          "label": "派生套餐",
          "relationTo": "billing.subscription-plan",
          "readOnly": true
        },
        {
          "type": "select",
          "name": "status",
          "label": "状态",
          "readOnly": true,
          "options": [
            {
              "label": "未使用",
              "value": "UNUSED",
              "color": "green"
            },
            {
              "label": "已兑换",
              "value": "REDEEMED",
              "color": "gray"
            },
            {
              "label": "已过期",
              "value": "EXPIRED",
              "color": "red"
            }
          ]
        },
        {
          "type": "date",
          "name": "expiresAt",
          "label": "过期时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "relationship",
          "name": "redeemedBy",
          "label": "兑换用户",
          "relationTo": "system.user",
          "readOnly": true
        },
        {
          "type": "date",
          "name": "redeemedAt",
          "label": "兑换时间",
          "readOnly": true,
          "includeTime": true
        },
        {
          "type": "text",
          "name": "remark",
          "label": "备注",
          "readOnly": true
        }
      ],
      "listView": {
        "columns": [
          "codePrefix",
          "creditAmount",
          "batchType",
          "type",
          "sku",
          "skuCode",
          "billingCycle",
          "plan",
          "status",
          "expiresAt",
          "redeemedBy",
          "redeemedAt"
        ],
        "defaultSort": "id:desc"
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
