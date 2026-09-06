/**
 * 候选版本比较对话框组件测试。
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { render, screen } from "@testing-library/react"
import { beforeEach, describe, expect, it, vi } from "vitest"

const { useAigcVersionComparison, useMediaVersionDetails } = vi.hoisted(() => ({
  useAigcVersionComparison: vi.fn(),
  useMediaVersionDetails: vi.fn()
}))

vi.mock("@/lib/api/rest/ai/aigc", () => ({ useAigcVersionComparison }))
vi.mock("@/lib/api/rest/media/media-asset", () => ({ useMediaVersionDetails }))
vi.mock("@/components/ui/dialog", () => ({
  Dialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
  DialogHeader: ({ children }: { children: ReactNode }) => <header>{children}</header>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h1>{children}</h1>
}))
vi.mock("@/components/ui/skeleton", () => ({
  Skeleton: () => <div data-testid="comparison-skeleton" />
}))

import { CandidateComparisonDialog } from "./CandidateComparisonDialog"

const baseProps = {
  open: true,
  projectId: 7,
  objectId: 12,
  leftVersionId: 31,
  rightVersionId: 32,
  onOpenChange: vi.fn()
}

function comparisonItem(objectVersionId: number, mediaVersionIds: number[]) {
  return {
    objectVersionId,
    status: objectVersionId === 31 ? "adopted" : "candidate",
    content: { text: `候选 ${objectVersionId}`, score: objectVersionId },
    documentVersionId: objectVersionId + 100,
    mediaVersionIds,
    sourceExecutionRunId: objectVersionId + 200,
    creditCost: 3,
    createdTime: "2026-09-05T08:00:00Z"
  }
}

function media(id: number, mediaType: "IMAGE" | "VIDEO") {
  return {
    id,
    name: `${mediaType}-${id}`,
    mediaType,
    currentVersion: { url: `https://example.test/${id}` }
  }
}

describe("CandidateComparisonDialog", () => {
  beforeEach(() => {
    useAigcVersionComparison.mockReset()
    useMediaVersionDetails.mockReset()
  })

  it("比较数据加载中显示双栏骨架", () => {
    useAigcVersionComparison.mockReturnValue({ isLoading: true })
    render(<CandidateComparisonDialog {...baseProps} />)
    expect(screen.getAllByTestId("comparison-skeleton")).toHaveLength(2)
  })

  it("并排展示左右候选内容、来源与图片视频", () => {
    useAigcVersionComparison.mockReturnValue({
      isLoading: false,
      data: {
        left: comparisonItem(31, [101]),
        right: comparisonItem(32, [102])
      }
    })
    useMediaVersionDetails.mockImplementation((ids: number[]) =>
      ids.map((id) => ({ data: media(id, id === 101 ? "IMAGE" : "VIDEO"), isLoading: false }))
    )

    const { container } = render(<CandidateComparisonDialog {...baseProps} />)

    expect(useMediaVersionDetails).toHaveBeenNthCalledWith(1, [101])
    expect(useMediaVersionDetails).toHaveBeenNthCalledWith(2, [102])

    expect(screen.getByText("候选 31")).toBeInTheDocument()
    expect(screen.getByText("候选 32")).toBeInTheDocument()
    expect(screen.getByText("来源 Run #231")).toBeInTheDocument()
    expect(screen.getByText("DocumentVersion #131")).toBeInTheDocument()
    expect(container.querySelectorAll("pre")).toHaveLength(2)
    expect(container.querySelector("pre")).toHaveTextContent('"score": 31')
    expect(screen.getByAltText("IMAGE-101")).toBeInTheDocument()
    expect(container.querySelector("video")).toHaveAttribute("src", "https://example.test/102")
  })

  it("无权威比较结果时显示失败态", () => {
    useAigcVersionComparison.mockReturnValue({ isLoading: false, data: undefined })
    render(<CandidateComparisonDialog {...baseProps} />)
    expect(screen.getByText("无法加载比较结果，请刷新权威版本后重试。")).toBeInTheDocument()
  })
})
