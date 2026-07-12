/**
 * org-store 单测——验证 ensureDefaultOrg 的默认组织选取逻辑
 * @author AaronZZH & Kiro
 */

import { beforeEach, describe, expect, it, vi } from "vitest"
import type { OrganizationVO } from "@/lib/api/rest/user/organization"

vi.mock("@/lib/api/rest/backend-client", () => ({
  setBackendOrgId: vi.fn()
}))

import { useOrgStore } from "./org-store"

function org(id: string, name = id): OrganizationVO {
  return { id, name, slug: id }
}

describe("useOrgStore.ensureDefaultOrg", () => {
  beforeEach(() => {
    useOrgStore.setState({ currentOrgId: null })
  })

  it("组织列表为空时不做任何操作", () => {
    useOrgStore.getState().ensureDefaultOrg([])
    expect(useOrgStore.getState().currentOrgId).toBeNull()
  })

  it("当前无选中组织时，选取列表第一个作为默认值", () => {
    const orgs = [org("org-1"), org("org-2")]
    useOrgStore.getState().ensureDefaultOrg(orgs)
    expect(useOrgStore.getState().currentOrgId).toBe("org-1")
  })

  it("当前选中组织仍在列表中时，保持不变（尊重用户上次选择）", () => {
    useOrgStore.setState({ currentOrgId: "org-2" })
    const orgs = [org("org-1"), org("org-2")]
    useOrgStore.getState().ensureDefaultOrg(orgs)
    expect(useOrgStore.getState().currentOrgId).toBe("org-2")
  })

  it("当前选中组织已不在列表中时，回退到列表第一个", () => {
    useOrgStore.setState({ currentOrgId: "org-stale" })
    const orgs = [org("org-1"), org("org-2")]
    useOrgStore.getState().ensureDefaultOrg(orgs)
    expect(useOrgStore.getState().currentOrgId).toBe("org-1")
  })
})
