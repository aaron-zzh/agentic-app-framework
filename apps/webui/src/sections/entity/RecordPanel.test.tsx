/**
 * RecordPanel 单元测试——验证详情查询窗口上下文透传
 */

import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

const viewEngineProps = vi.hoisted(() => ({
  current: undefined as
    | {
        recordId?: string
        queryToken?: string
        onRecordChange?: (recordId: string) => void
        showRecordWindowPager?: boolean
      }
    | undefined
}))

const recordWindowNavigationProps = vi.hoisted(() => ({
  current: undefined as
    | {
        recordId?: string
        queryToken?: string
        onRecordChange?: (recordId: string) => void
      }
    | undefined
}))

vi.mock("@/features/entity-engine/components", () => ({
  ViewEngine: ({
    recordId,
    queryToken,
    onRecordChange,
    showRecordWindowPager
  }: {
    recordId?: string
    queryToken?: string
    onRecordChange?: (recordId: string) => void
    showRecordWindowPager?: boolean
  }) => {
    viewEngineProps.current = { recordId, queryToken, onRecordChange, showRecordWindowPager }
    return (
      <button type="button" onClick={() => onRecordChange?.("2")}>
        切换记录
      </button>
    )
  }
}))

vi.mock("@/components/ui/resizable", () => ({
  ResizablePanelGroup: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ResizablePanel: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  ResizableHandle: () => <div />
}))

vi.mock("@/features/entity-engine/components/ViewEngine", () => ({
  useRecordWindowNavigation: ({
    recordId,
    queryToken,
    onRecordChange
  }: {
    recordId?: string
    queryToken?: string
    onRecordChange?: (recordId: string) => void
  }) => {
    recordWindowNavigationProps.current = { recordId, queryToken, onRecordChange }
    return {
      statusLabel: "当前窗口 1 / 2",
      isAvailable: true,
      prevTitle: "上一条",
      nextTitle: "下一条",
      navigateToRecord: vi.fn()
    }
  },
  RecordWindowNavigationControls: () => <div data-testid="record-window-navigation">记录切换</div>
}))

import type { EntityDef } from "@/features/entity-engine/types"
import { RecordPanel } from "./RecordPanel"

const entity: EntityDef = {
  slug: "task",
  label: "任务",
  apiPath: "/tasks",
  fields: [],
  listView: { columns: [] }
}

describe("RecordPanel", () => {
  it("应将查询窗口和切换回调传递给详情视图", () => {
    const onRecordChange = vi.fn()

    render(
      <RecordPanel
        entity={entity}
        recordId="1"
        queryToken="window-1"
        onClose={vi.fn()}
        onRecordChange={onRecordChange}
      >
        <div>列表</div>
      </RecordPanel>
    )

    expect(viewEngineProps.current).toMatchObject({
      recordId: "1",
      queryToken: "window-1",
      showRecordWindowPager: false
    })
    expect(recordWindowNavigationProps.current).toMatchObject({
      recordId: "1",
      queryToken: "window-1"
    })
    expect(screen.getByTestId("record-window-navigation")).toBeInTheDocument()
    expect(screen.getByText("当前窗口 1 / 2")).toBeInTheDocument()
    fireEvent.click(screen.getByRole("button", { name: "切换记录" }))
    expect(onRecordChange).toHaveBeenCalledWith("2")
  })
})
