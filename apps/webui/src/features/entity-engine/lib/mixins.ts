/**
 * 内置 Mixin 定义——对应后端 BaseEntity 字段集合
 * @author AaronZZH & Kiro
 */

import type { FieldDef } from "../types"

/** Mixin 定义：一组可复用的字段集合 */
export interface MixinDef {
  name: string
  fields: FieldDef[]
}

/** 时间戳 Mixin（createTime / updateTime） */
export const TimestampMixin: MixinDef = {
  name: "timestamp",
  fields: [
    { type: "date", name: "createTime", label: "创建时间", readOnly: true, includeTime: true },
    { type: "date", name: "updateTime", label: "更新时间", readOnly: true, includeTime: true },
  ],
}

/** 审计 Mixin（createBy / updateBy） */
export const AuditMixin: MixinDef = {
  name: "audit",
  fields: [
    { type: "relationship", name: "createBy", label: "创建人", relationTo: "user", readOnly: true },
    { type: "relationship", name: "updateBy", label: "更新人", relationTo: "user", readOnly: true },
  ],
}

/** 软删除 Mixin（deleted / deleteTime） */
export const SoftDeleteMixin: MixinDef = {
  name: "softDelete",
  fields: [
    { type: "checkbox", name: "deleted", label: "已删除", hidden: true, defaultValue: false },
    { type: "date", name: "deleteTime", label: "删除时间", hidden: true, includeTime: true },
  ],
}

/** 多租户 Mixin（orgId） */
export const OrgMixin: MixinDef = {
  name: "org",
  fields: [
    { type: "relationship", name: "orgId", label: "所属组织", relationTo: "organization", hidden: true },
  ],
}

/** 备注 Mixin（remark） */
export const RemarkMixin: MixinDef = {
  name: "remark",
  fields: [
    { type: "textarea", name: "remark", label: "备注" },
  ],
}

/**
 * 基础实体 Mixin（对应后端 BaseEntity 全部字段）
 * 包含：timestamp + audit + softDelete + remark
 */
export const BaseEntityMixin: MixinDef = {
  name: "baseEntity",
  fields: [
    ...TimestampMixin.fields,
    ...AuditMixin.fields,
    ...SoftDeleteMixin.fields,
    ...RemarkMixin.fields,
  ],
}

/** 内置 Mixin 注册表 */
export const builtinMixins: Record<string, MixinDef> = {
  timestamp: TimestampMixin,
  audit: AuditMixin,
  softDelete: SoftDeleteMixin,
  org: OrgMixin,
  remark: RemarkMixin,
  baseEntity: BaseEntityMixin,
}
