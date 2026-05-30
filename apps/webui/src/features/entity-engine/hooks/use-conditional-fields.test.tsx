/**
 * useConditionalFields 单元测试——验证条件可见性引擎
 */

import { renderHook } from "@testing-library/react"
import type { ReactNode } from "react"
import { FormProvider, useForm } from "react-hook-form"
import { describe, expect, it } from "vitest"
import type { DataFieldDef } from "@/features/entity-engine/types"
import { useConditionalFields } from "./use-conditional-fields"

/** 包装 FormProvider 的 wrapper */
function createWrapper(defaultValues: Record<string, unknown>) {
  return function Wrapper({ children }: { children: ReactNode }) {
    const form = useForm({ defaultValues })
    return <FormProvider {...form}>{children}</FormProvider>
  }
}

describe("useConditionalFields", () => {
  const fields: DataFieldDef[] = [
    { name: "name", label: "名称", type: "string", required: true },
    { name: "type", label: "类型", type: "select" },
    { name: "details", label: "详情", type: "text", visibleWhen: "type == 'custom'" },
    { name: "amount", label: "金额", type: "number", readOnlyWhen: "type == 'fixed'" },
    { name: "note", label: "备注", type: "string", requiredWhen: "amount > 1000" }
  ]

  it("无条件字段应默认 visible=true, readOnly=false, required 取字段定义", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ name: "", type: "" })
    })

    expect(result.current.name.visible).toBe(true)
    expect(result.current.name.readOnly).toBe(false)
    expect(result.current.name.required).toBe(true)
  })

  it("visibleWhen 条件不满足时 visible=false", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ type: "standard" })
    })

    expect(result.current.details.visible).toBe(false)
  })

  it("visibleWhen 条件满足时 visible=true", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ type: "custom" })
    })

    expect(result.current.details.visible).toBe(true)
  })

  it("readOnlyWhen 条件满足时 readOnly=true", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ type: "fixed" })
    })

    expect(result.current.amount.readOnly).toBe(true)
  })

  it("requiredWhen 条件满足时 required=true", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ amount: 2000 })
    })

    expect(result.current.note.required).toBe(true)
  })

  it("requiredWhen 条件不满足时 required=false", () => {
    const { result } = renderHook(() => useConditionalFields(fields), {
      wrapper: createWrapper({ amount: 500 })
    })

    expect(result.current.note.required).toBe(false)
  })
})
