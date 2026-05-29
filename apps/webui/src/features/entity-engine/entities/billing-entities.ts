/**
 * 会员中心实体配置——等级/订阅/权益/账单
 * @author AaronZZH & Kiro
 */

import type { EntityDef } from "../types"

/** 会员等级 */
export const levelEntity: EntityDef = {
  slug: "level",
  label: "会员等级",
  labelPlural: "会员等级",
  apiPath: "/api/level",
  icon: "crown",
  group: "billing",
  groupLabel: "会员中心",
  fields: [
    { type: "text", name: "code", label: "等级编码", required: true },
    { type: "text", name: "name", label: "等级名称", required: true },
    { type: "number", name: "expMin", label: "最低成长值", required: true },
    { type: "number", name: "expMax", label: "最高成长值", required: true },
    { type: "number", name: "sort", label: "排序" }
  ],
  listView: {
    columns: ["code", "name", "expMin", "expMax", "sort"],
    defaultSort: "sort:asc",
    searchableFields: ["code", "name"]
  },
  mixins: ["baseEntity"]
}

/** 订阅套餐 */
export const subscriptionPlanEntity: EntityDef = {
  slug: "subscription-plan",
  label: "订阅套餐",
  labelPlural: "订阅套餐",
  apiPath: "/api/subscription/plans",
  icon: "credit-card",
  group: "billing",
  groupLabel: "会员中心",
  fields: [
    { type: "text", name: "code", label: "套餐编码", required: true },
    { type: "text", name: "name", label: "套餐名称", required: true },
    { type: "number", name: "durationDays", label: "有效天数" },
    { type: "number", name: "price", label: "售价（分）", required: true },
    { type: "number", name: "marketPrice", label: "市场价（分）" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "启用", value: "ENABLED", color: "green" },
        { label: "停用", value: "DISABLED", color: "gray" }
      ]
    },
    { type: "number", name: "sort", label: "排序" }
  ],
  listView: {
    columns: ["code", "name", "durationDays", "price", "marketPrice", "status", "sort"],
    defaultSort: "sort:asc",
    searchableFields: ["code", "name"],
    filterableFields: ["status"]
  },
  formView: {
    layout: [
      {
        type: "group",
        label: "基本信息",
        fields: [
          { type: "text", name: "code", label: "套餐编码", required: true },
          { type: "text", name: "name", label: "套餐名称", required: true },
          { type: "number", name: "durationDays", label: "有效天数" },
          {
            type: "row",
            fields: [
              { type: "number", name: "price", label: "售价（分）", required: true },
              { type: "number", name: "marketPrice", label: "市场价（分）" }
            ]
          },
          {
            type: "select",
            name: "status",
            label: "状态",
            options: [
              { label: "启用", value: "ENABLED", color: "green" },
              { label: "停用", value: "DISABLED", color: "gray" }
            ]
          },
          { type: "number", name: "sort", label: "排序" }
        ]
      }
    ]
  },
  mixins: ["baseEntity"]
}

/** 用户订阅 */
export const subscriptionEntity: EntityDef = {
  slug: "subscription",
  label: "用户订阅",
  labelPlural: "用户订阅",
  apiPath: "/api/subscription",
  icon: "zap",
  group: "billing",
  groupLabel: "会员中心",
  fields: [
    { type: "relationship", name: "userId", label: "用户", relationTo: "user" },
    { type: "relationship", name: "planId", label: "套餐", relationTo: "subscription-plan" },
    { type: "date", name: "startAt", label: "开始时间", includeTime: true },
    { type: "date", name: "endAt", label: "到期时间", includeTime: true },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "生效中", value: "ACTIVE", color: "green" },
        { label: "已过期", value: "EXPIRED", color: "gray" },
        { label: "已取消", value: "CANCELLED", color: "red" }
      ]
    }
  ],
  listView: {
    columns: ["userId", "planId", "startAt", "endAt", "status"],
    defaultSort: "startAt:desc",
    filterableFields: ["status"],
    quickFilters: [
      { label: "生效中", field: "status", operator: "eq", value: "ACTIVE" },
      { label: "已过期", field: "status", operator: "eq", value: "EXPIRED" },
      { label: "已取消", field: "status", operator: "eq", value: "CANCELLED" }
    ]
  },
  mixins: ["baseEntity"]
}

/** 权益额度 */
export const entitlementQuotaEntity: EntityDef = {
  slug: "entitlement-quota",
  label: "权益额度",
  labelPlural: "权益额度",
  apiPath: "/api/entitlement/quotas",
  icon: "wallet",
  group: "billing",
  groupLabel: "会员中心",
  fields: [
    { type: "text", name: "entName", label: "权益名称", readOnly: true },
    { type: "number", name: "total", label: "总额度" },
    { type: "number", name: "used", label: "已用" },
    { type: "number", name: "remain", label: "剩余" },
    { type: "date", name: "nextResetAt", label: "下次重置时间", includeTime: true }
  ],
  listView: {
    columns: ["entName", "total", "used", "remain", "nextResetAt"],
    defaultSort: "nextResetAt:asc",
    searchableFields: ["entName"]
  },
  mixins: ["baseEntity"]
}

/** 账单流水（积分） */
export const walletTransactionEntity: EntityDef = {
  slug: "wallet-transaction",
  label: "积分流水",
  labelPlural: "积分流水",
  apiPath: "/api/billing/transactions",
  icon: "receipt",
  group: "billing",
  groupLabel: "会员中心",
  fields: [
    {
      type: "select",
      name: "type",
      label: "类型",
      options: [
        { label: "充值", value: "RECHARGE", color: "green" },
        { label: "消费", value: "CONSUME", color: "red" },
        { label: "退款", value: "REFUND", color: "orange" },
        { label: "赠送", value: "GRANT", color: "blue" }
      ]
    },
    { type: "number", name: "pointDelta", label: "变动金额" },
    { type: "number", name: "balanceAfter", label: "变动后余额" },
    { type: "text", name: "refType", label: "来源类型" },
    { type: "date", name: "createdAt", label: "时间", includeTime: true }
  ],
  listView: {
    columns: ["type", "pointDelta", "balanceAfter", "refType", "createdAt"],
    defaultSort: "createdAt:desc",
    filterableFields: ["type"],
    quickFilters: [
      { label: "收入", field: "type", operator: "in", value: "RECHARGE,GRANT" },
      { label: "支出", field: "type", operator: "in", value: "CONSUME,REFUND" }
    ]
  },
  mixins: ["baseEntity"]
}

/** 会员中心所有实体 */
export const billingEntities: EntityDef[] = [
  levelEntity,
  subscriptionPlanEntity,
  subscriptionEntity,
  entitlementQuotaEntity,
  walletTransactionEntity
]
