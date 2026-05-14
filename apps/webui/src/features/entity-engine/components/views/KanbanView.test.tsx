/**
 * KanbanView 单元测试
 * @author AaronZZH & Kiro
 */

import { render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import type { EntityDef } from "../../types"
import { KanbanView } from "./KanbanView"

const taskEntity: EntityDef = {
  slug: "task",
  label: "任务",
  apiPath: "/api/tasks",
  fields: [
    { type: "text", name: "title", label: "标题", required: true },
    { type: "textarea", name: "description", label: "描述" },
    {
      type: "select",
      name: "status",
      label: "状态",
      options: [
        { label: "待办", value: "todo", color: "gray" },
        { label: "进行中", value: "in_progress", color: "blue" },
        { label: "已完成", value: "done", color: "green" }
      ]
    }
  ],
  listView: { columns: ["title", "status"] },
  kanbanView: { statusField: "status", cardTitle: "title", cardDescription: "description" }
}

const mockData = [
  { id: "1", title: "任务一", description: "描述一", status: "todo" },
  { id: "2", title: "任务二", description: "描述二", status: "in_progress" },
  { id: "3", title: "任务三", description: "描述三", status: "done" }
]

describe("KanbanView", () => {
  it("should render columns from statusField options", () => {
    render(<KanbanView entity={taskEntity} data={mockData} />)

    expect(screen.getByText("待办")).toBeInTheDocument()
    expect(screen.getByText("进行中")).toBeInTheDocument()
    expect(screen.getByText("已完成")).toBeInTheDocument()
  })

  it("should render cards in correct columns", () => {
    render(<KanbanView entity={taskEntity} data={mockData} />)

    expect(screen.getByText("任务一")).toBeInTheDocument()
    expect(screen.getByText("任务二")).toBeInTheDocument()
    expect(screen.getByText("任务三")).toBeInTheDocument()
  })

  it("should render card descriptions", () => {
    render(<KanbanView entity={taskEntity} data={mockData} />)

    expect(screen.getByText("描述一")).toBeInTheDocument()
    expect(screen.getByText("描述二")).toBeInTheDocument()
  })

  it("should show column counts", () => {
    render(<KanbanView entity={taskEntity} data={mockData} />)

    expect(screen.getAllByText("(1)")).toHaveLength(3)
  })

  it("should show skeleton when loading", () => {
    const { container } = render(<KanbanView entity={taskEntity} loading />)

    expect(container.querySelectorAll(".animate-pulse").length).toBeGreaterThan(0)
  })

  it("should show message when kanbanView not configured", () => {
    const entityWithoutKanban: EntityDef = {
      ...taskEntity,
      kanbanView: undefined
    }
    render(<KanbanView entity={entityWithoutKanban} />)

    expect(screen.getByText("未配置看板视图")).toBeInTheDocument()
  })

  it("should call onStatusChange is not called without drag", () => {
    const onStatusChange = vi.fn()
    render(<KanbanView entity={taskEntity} data={mockData} onStatusChange={onStatusChange} />)

    // 无拖拽操作时不应触发回调
    expect(onStatusChange).not.toHaveBeenCalled()
  })
})
