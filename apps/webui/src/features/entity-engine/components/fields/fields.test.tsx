/**
 * 基础字段组件测试
 * @author AaronZZH & Kiro
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { SelectField, TextField } from "../../types"

import { CheckboxInput } from "./CheckboxInput"
import { SelectInput } from "./SelectInput"
import { TextInput } from "./TextInput"

const textField: TextField = { type: "text", name: "title", label: "标题", placeholder: "请输入标题" }

describe("TextInput", () => {
  it("渲染标签和输入框", () => {
    render(<TextInput name="title" value="" onChange={() => {}} field={textField} />)
    expect(screen.getByText("标题")).toBeInTheDocument()
    expect(screen.getByPlaceholderText("请输入标题")).toBeInTheDocument()
  })

  it("触发 onChange", () => {
    const onChange = vi.fn()
    render(<TextInput name="title" value="" onChange={onChange} field={textField} />)
    fireEvent.change(screen.getByPlaceholderText("请输入标题"), { target: { value: "新标题" } })
    expect(onChange).toHaveBeenCalledWith("新标题")
  })

  it("显示错误信息", () => {
    render(<TextInput name="title" value="" onChange={() => {}} field={textField} error="必填" />)
    expect(screen.getByText("必填")).toBeInTheDocument()
  })

  it("禁用状态", () => {
    render(<TextInput name="title" value="" onChange={() => {}} field={textField} disabled />)
    expect(screen.getByPlaceholderText("请输入标题")).toBeDisabled()
  })
})

describe("CheckboxInput", () => {
  const checkField = { type: "checkbox" as const, name: "active", label: "启用" }

  it("渲染复选框", () => {
    render(<CheckboxInput name="active" value={false} onChange={() => {}} field={checkField} />)
    expect(screen.getByText("启用")).toBeInTheDocument()
    expect(screen.getByRole("checkbox")).not.toBeChecked()
  })

  it("触发 onChange", () => {
    const onChange = vi.fn()
    render(<CheckboxInput name="active" value={false} onChange={onChange} field={checkField} />)
    fireEvent.click(screen.getByRole("checkbox"))
    expect(onChange).toHaveBeenCalledWith(true)
  })
})

describe("SelectInput", () => {
  const selectField: SelectField = {
    type: "select",
    name: "status",
    label: "状态",
    options: [
      { label: "草稿", value: "draft" },
      { label: "已发布", value: "published" },
    ],
  }

  it("渲染选项", () => {
    render(<SelectInput name="status" value="" onChange={() => {}} field={selectField} />)
    expect(screen.getByText("草稿")).toBeInTheDocument()
    expect(screen.getByText("已发布")).toBeInTheDocument()
  })

  it("触发 onChange", () => {
    const onChange = vi.fn()
    render(<SelectInput name="status" value="" onChange={onChange} field={selectField} />)
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "published" } })
    expect(onChange).toHaveBeenCalledWith("published")
  })
})
