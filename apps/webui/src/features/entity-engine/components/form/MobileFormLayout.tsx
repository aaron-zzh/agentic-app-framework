/**
 * MobileFormLayout——手机端表单布局（tabs 改为垂直折叠）
 * @author AaronZZH & Kiro
 *
 * 表单在 <768px 时：
 * - tabs 布局改为 Accordion 垂直折叠
 * - group 默认折叠
 * - row 改为单列堆叠
 */

"use client"

import { ChevronDown } from "lucide-react"
import { useState } from "react"
import { cn } from "@/lib/utils/cn"
import type { FieldDef, GroupField, LayoutField, RowField, TabsField } from "../../types"

interface MobileFormLayoutProps {
  /** 表单布局配置 */
  layout: LayoutField[]
  /** 字段渲染函数 */
  renderField: (field: FieldDef) => React.ReactNode
}

/** 手机端表单布局 */
export function MobileFormLayout({ layout, renderField }: MobileFormLayoutProps) {
  return (
    <div className="flex flex-col gap-4 p-4">
      {layout.map((item, idx) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: 布局项无稳定 key
        <MobileLayoutItem key={idx} item={item} renderField={renderField} />
      ))}
    </div>
  )
}

/** 布局项分发 */
function MobileLayoutItem({
  item,
  renderField
}: {
  item: LayoutField
  renderField: (field: FieldDef) => React.ReactNode
}) {
  switch (item.type) {
    case "tabs":
      return <MobileTabs tabs={item} renderField={renderField} />
    case "group":
      return <MobileGroup group={item} renderField={renderField} />
    case "row":
      return <MobileRow row={item} renderField={renderField} />
    default:
      return null
  }
}

/** Tabs → 垂直折叠 Accordion */
function MobileTabs({
  tabs,
  renderField
}: {
  tabs: TabsField
  renderField: (field: FieldDef) => React.ReactNode
}) {
  const [openIndex, setOpenIndex] = useState(0)

  return (
    <div className="flex flex-col gap-1 rounded-lg border">
      {tabs.tabs.map((tab, idx) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: tab 项无稳定 key
        <div key={idx}>
          <button
            type="button"
            onClick={() => setOpenIndex(openIndex === idx ? -1 : idx)}
            className="flex w-full items-center justify-between px-4 py-3 text-left font-medium text-sm"
          >
            {tab.label}
            <ChevronDown
              className={cn("size-4 transition-transform", openIndex === idx && "rotate-180")}
            />
          </button>
          {openIndex === idx && (
            <div className="flex flex-col gap-3 border-t px-4 py-3">
              {tab.fields.map((field) => renderField(field))}
            </div>
          )}
        </div>
      ))}
    </div>
  )
}

/** Group → 可折叠区块 */
function MobileGroup({
  group,
  renderField
}: {
  group: GroupField
  renderField: (field: FieldDef) => React.ReactNode
}) {
  const [open, setOpen] = useState(!group.defaultCollapsed)

  return (
    <div className="rounded-lg border">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex w-full items-center justify-between px-4 py-3 text-left font-medium text-sm"
      >
        {group.label}
        <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} />
      </button>
      {open && (
        <div className="flex flex-col gap-3 border-t px-4 py-3">
          {group.fields.map((field) => renderField(field))}
        </div>
      )}
    </div>
  )
}

/** Row → 单列堆叠（手机端不并排） */
function MobileRow({
  row,
  renderField
}: {
  row: RowField
  renderField: (field: FieldDef) => React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-3">
      {row.fields.map((field) => renderField(field))}
    </div>
  )
}
