import { render, screen } from "@testing-library/react"
import type { ComponentProps } from "react"
import { describe, expect, it, vi } from "vitest"
import type { RelationshipField } from "@/lib/types/entity"

const { mockGetByResource } = vi.hoisted(() => ({
  mockGetByResource: vi.fn()
}))

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: ComponentProps<"a">) => (
    <a href={href} {...props}>
      {children}
    </a>
  )
}))

vi.mock("@/lib/modules/entity-registry", () => ({
  entityRegistry: { getByResource: mockGetByResource }
}))

import { RelationCell } from "./index"

describe("RelationCell", () => {
  it("使用目标实体的标准记录 URL", () => {
    mockGetByResource.mockReturnValue({ slug: "user" })

    render(
      <RelationCell
        value={{ id: 7, displayName: "张三" }}
        record={{}}
        field={
          { type: "relationship", name: "assignee", relationTo: "system.user" } as RelationshipField
        }
      />
    )

    expect(screen.getByRole("link", { name: /张三$/ })).toHaveAttribute("href", "/module/user/7")
  })
})
