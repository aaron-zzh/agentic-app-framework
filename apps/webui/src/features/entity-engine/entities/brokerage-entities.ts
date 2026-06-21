/**
 * 分销模块实体配置
 * @author AaronZZH & Kiro
 */

import type { EntityDef } from "../types"

/** 分销员 */
export const brokerageUserEntity: EntityDef = {
  slug: "brokerage-user",
  label: "分销员",
  labelPlural: "分销员",
  apiPath: "/brokerage/users",
  icon: "users",
  group: "brokerage",
  groupLabel: "分销",
  fields: [
    { type: "number", name: "contactId", label: "联系人 ID", required: true },
    { type: "number", name: "referrerContactId", label: "推荐人 ID" },
    {
      type: "date",
      includeTime: true,
      name: "referrerBindTime",
      label: "绑定时间",
      readOnly: true
    },
    { type: "switch", name: "brokerageEnabled", label: "分销资格" },
    {
      type: "date",
      includeTime: true,
      name: "brokerageTime",
      label: "成为分销员时间",
      readOnly: true
    },
    { type: "number", name: "balance", label: "可用佣金（分）", readOnly: true },
    { type: "number", name: "frozen", label: "冻结佣金（分）", readOnly: true }
  ],
  listView: {
    columns: [
      "contactId",
      "referrerContactId",
      "brokerageEnabled",
      "balance",
      "frozen",
      "createTime"
    ],
    defaultSort: "createTime:desc",
    searchableFields: ["contactId"],
    filterableFields: ["brokerageEnabled"]
  },
  formView: {
    layout: [
      {
        type: "group",
        label: "分销信息",
        fields: [
          {
            type: "row",
            fields: [
              { type: "number", name: "contactId", label: "联系人 ID", required: true },
              { type: "number", name: "referrerContactId", label: "推荐人 ID" }
            ]
          },
          { type: "switch", name: "brokerageEnabled", label: "是否有分销资格" }
        ]
      },
      {
        type: "group",
        label: "余额（只读）",
        fields: [
          {
            type: "row",
            fields: [
              { type: "number", name: "balance", label: "可用佣金（分）", readOnly: true },
              { type: "number", name: "frozen", label: "冻结佣金（分）", readOnly: true }
            ]
          }
        ]
      }
    ]
  },
  mixins: ["baseEntity"]
}

/** 佣金流水 */
export const brokerageRecordEntity: EntityDef = {
  slug: "brokerage-record",
  label: "佣金流水",
  labelPlural: "佣金流水",
  apiPath: "/brokerage/records",
  icon: "receipt",
  group: "brokerage",
  groupLabel: "分销",
  access: { read: true, create: false, update: false, delete: false },
  fields: [
    { type: "number", name: "contactId", label: "分销员 ID" },
    { type: "number", name: "sourceContactId", label: "来源联系人 ID" },
    { type: "number", name: "sourceLevel", label: "层级" },
    { type: "text", name: "bizType", label: "业务类型" },
    { type: "text", name: "bizId", label: "业务 ID" },
    { type: "text", name: "title", label: "标题" },
    { type: "number", name: "amount", label: "金额（分）" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "冻结中", value: "FROZEN", color: "orange" },
        { label: "可用", value: "VALID", color: "green" },
        { label: "已取消", value: "CANCELLED", color: "gray" }
      ]
    },
    { type: "date", includeTime: true, name: "unfreezeTime", label: "解冻时间" }
  ],
  listView: {
    columns: [
      "contactId",
      "bizType",
      "title",
      "amount",
      "status",
      "sourceLevel",
      "unfreezeTime",
      "createTime"
    ],
    defaultSort: "createTime:desc",
    searchableFields: ["bizId", "title"],
    filterableFields: ["status", "bizType"],
    quickFilters: [
      { label: "冻结中", field: "status", operator: "eq", value: "FROZEN" },
      { label: "可用", field: "status", operator: "eq", value: "VALID" }
    ]
  },
  mixins: ["baseEntity"]
}

/** 提现申请 */
export const brokerageWithdrawEntity: EntityDef = {
  slug: "brokerage-withdraw",
  label: "提现申请",
  labelPlural: "提现申请",
  apiPath: "/brokerage/withdraws",
  icon: "banknote",
  group: "brokerage",
  groupLabel: "分销",
  fields: [
    { type: "number", name: "contactId", label: "申请人 ID", required: true },
    { type: "number", name: "amount", label: "申请金额（分）", required: true },
    { type: "number", name: "fee", label: "手续费（分）", readOnly: true },
    {
      type: "select",
      name: "type",
      label: "提现方式",
      options: [
        { label: "微信", value: "WECHAT" },
        { label: "支付宝", value: "ALIPAY" },
        { label: "银行卡", value: "BANK" }
      ],
      required: true
    },
    { type: "text", name: "accountName", label: "收款人姓名" },
    { type: "text", name: "accountNo", label: "收款账号" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "待审核", value: "PENDING", color: "orange" },
        { label: "已通过", value: "APPROVED", color: "blue" },
        { label: "已拒绝", value: "REJECTED", color: "red" },
        { label: "已转账", value: "TRANSFERRED", color: "green" }
      ]
    },
    { type: "text", name: "auditReason", label: "审核意见" },
    { type: "date", includeTime: true, name: "auditTime", label: "审核时间", readOnly: true },
    { type: "date", includeTime: true, name: "transferTime", label: "转账时间", readOnly: true }
  ],
  listView: {
    columns: ["contactId", "amount", "type", "accountName", "status", "auditTime", "createTime"],
    defaultSort: "createTime:desc",
    filterableFields: ["status", "type"],
    quickFilters: [
      { label: "待审核", field: "status", operator: "eq", value: "PENDING" },
      { label: "已通过", field: "status", operator: "eq", value: "APPROVED" }
    ]
  },
  formView: {
    layout: [
      {
        type: "group",
        label: "提现信息",
        fields: [
          {
            type: "row",
            fields: [
              { type: "number", name: "contactId", label: "申请人 ID", required: true },
              { type: "number", name: "amount", label: "申请金额（分）", required: true }
            ]
          },
          {
            type: "row",
            fields: [
              {
                type: "select",
                name: "type",
                label: "提现方式",
                options: [
                  { label: "微信", value: "WECHAT" },
                  { label: "支付宝", value: "ALIPAY" },
                  { label: "银行卡", value: "BANK" }
                ],
                required: true
              },
              { type: "text", name: "accountName", label: "收款人姓名" }
            ]
          },
          { type: "text", name: "accountNo", label: "收款账号" }
        ]
      },
      {
        type: "group",
        label: "审核信息",
        fields: [
          {
            type: "select",
            name: "status",
            label: "状态",
            options: [
              { label: "待审核", value: "PENDING", color: "orange" },
              { label: "已通过", value: "APPROVED", color: "blue" },
              { label: "已拒绝", value: "REJECTED", color: "red" },
              { label: "已转账", value: "TRANSFERRED", color: "green" }
            ]
          },
          { type: "text", name: "auditReason", label: "审核意见" }
        ]
      }
    ]
  },
  mixins: ["baseEntity"]
}

/** 佣金规则 */
export const brokerageRuleEntity: EntityDef = {
  slug: "brokerage-rule",
  label: "佣金规则",
  labelPlural: "佣金规则",
  apiPath: "/brokerage/rules",
  icon: "percent",
  group: "brokerage",
  groupLabel: "分销",
  fields: [
    { type: "text", name: "name", label: "规则名称", required: true },
    { type: "text", name: "bizType", label: "业务类型", required: true },
    { type: "text", name: "bizTargetType", label: "目标类型" },
    { type: "text", name: "bizTargetId", label: "目标 ID" },
    { type: "number", name: "level1Rate", label: "一级比例" },
    { type: "number", name: "level2Rate", label: "二级比例" },
    {
      type: "select",
      name: "calcBase",
      label: "计算基准",
      options: [
        { label: "按金额比例", value: "AMOUNT" },
        { label: "固定金额", value: "FIXED" }
      ]
    },
    { type: "number", name: "fixedAmount", label: "固定金额（分）" },
    { type: "number", name: "frozenDays", label: "冻结天数" },
    { type: "number", name: "priority", label: "优先级" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "启用", value: "ENABLED", color: "green" },
        { label: "停用", value: "DISABLED", color: "gray" }
      ]
    }
  ],
  listView: {
    columns: [
      "name",
      "bizType",
      "bizTargetType",
      "level1Rate",
      "level2Rate",
      "frozenDays",
      "status",
      "priority"
    ],
    defaultSort: "priority:asc",
    searchableFields: ["name", "bizType"],
    filterableFields: ["status", "bizType"]
  },
  formView: {
    layout: [
      {
        type: "group",
        label: "匹配条件",
        fields: [
          { type: "text", name: "name", label: "规则名称", required: true },
          {
            type: "row",
            fields: [
              { type: "text", name: "bizType", label: "业务类型", required: true },
              { type: "text", name: "bizTargetType", label: "目标类型" }
            ]
          },
          { type: "text", name: "bizTargetId", label: "目标 ID" }
        ]
      },
      {
        type: "group",
        label: "佣金设置",
        fields: [
          {
            type: "row",
            fields: [
              { type: "number", name: "level1Rate", label: "一级比例" },
              { type: "number", name: "level2Rate", label: "二级比例" }
            ]
          },
          {
            type: "row",
            fields: [
              {
                type: "select",
                name: "calcBase",
                label: "计算基准",
                options: [
                  { label: "按金额比例", value: "AMOUNT" },
                  { label: "固定金额", value: "FIXED" }
                ]
              },
              { type: "number", name: "fixedAmount", label: "固定金额（分）" }
            ]
          },
          {
            type: "row",
            fields: [
              { type: "number", name: "frozenDays", label: "冻结天数" },
              { type: "number", name: "priority", label: "优先级" }
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
          }
        ]
      }
    ]
  },
  mixins: ["baseEntity"]
}

/** 会员等级佣金加成 */
export const brokerageLevelBonusEntity: EntityDef = {
  slug: "brokerage-level-bonus",
  label: "等级加成",
  labelPlural: "等级加成",
  apiPath: "/brokerage/level-bonuses",
  icon: "crown",
  group: "brokerage",
  groupLabel: "分销",
  fields: [
    { type: "number", name: "ruleId", label: "规则 ID", required: true },
    { type: "number", name: "planId", label: "套餐 ID", required: true },
    { type: "number", name: "level1Rate", label: "一级比例", required: true },
    { type: "number", name: "level2Rate", label: "二级比例", required: true }
  ],
  listView: {
    columns: ["ruleId", "planId", "level1Rate", "level2Rate", "createTime"],
    defaultSort: "createTime:desc"
  },
  formView: {
    layout: [
      {
        type: "group",
        label: "加成配置",
        fields: [
          {
            type: "row",
            fields: [
              { type: "number", name: "ruleId", label: "规则 ID", required: true },
              { type: "number", name: "planId", label: "套餐 ID", required: true }
            ]
          },
          {
            type: "row",
            fields: [
              { type: "number", name: "level1Rate", label: "一级比例覆盖", required: true },
              { type: "number", name: "level2Rate", label: "二级比例覆盖", required: true }
            ]
          }
        ]
      }
    ]
  },
  mixins: ["baseEntity"]
}

export const brokerageEntities: EntityDef[] = [
  brokerageUserEntity,
  brokerageRecordEntity,
  brokerageWithdrawEntity,
  brokerageRuleEntity,
  brokerageLevelBonusEntity
]
