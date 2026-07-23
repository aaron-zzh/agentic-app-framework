import { render, screen } from "@testing-library/react"
import type { ReactNode } from "react"
import { describe, expect, it, vi } from "vitest"

const { mockBack, mockGet } = vi.hoisted(() => ({
  mockBack: vi.fn(),
  mockGet: vi.fn()
}))

vi.mock("next/navigation", () => ({
  useRouter: () => ({ back: mockBack })
}))

vi.mock("@/components/ui/dialog", () => ({
  Dialog: ({ children }: { children: ReactNode }) => <div role="dialog">{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <header>{children}</header>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>
}))

vi.mock("@/features/entity-engine/components", () => ({
  ViewEngine: ({ recordId, queryToken }: { recordId?: string; queryToken?: string }) => (
    <div data-testid="view-engine">{`${recordId}:${queryToken}`}</div>
  )
}))

vi.mock("@/lib/modules/entity-registry", () => ({
  entityRegistry: { get: mockGet }
}))

import { EntityRecordInterceptModal } from "./EntityRecordInterceptModal"

describe("EntityRecordInterceptModal", () => {
  it("在拦截路由中加载实体详情", () => {
    mockGet.mockReturnValue({ slug: "todo", label: "待办" })

    render(<EntityRecordInterceptModal module="todo" recordId="2" queryToken="window-token" />)

    expect(screen.getByRole("dialog")).toBeInTheDocument()
    expect(screen.getByRole("heading", { name: "待办详情" })).toBeInTheDocument()
    expect(screen.getByTestId("view-engine")).toHaveTextContent("2:window-token")
  })
})
