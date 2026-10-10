/**
 * useEntityQueryWindow 缓存键单元测试
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import type { EntityDef } from "@/lib/types/entity"
import { entityQueryWindowKey } from "./use-entity-query-window"

describe("entityQueryWindowKey", () => {
  it("查询窗口缓存使用 CRUD 资源前缀", () => {
    const entity = { slug: "task", apiPath: "/api/task" } as EntityDef

    expect(entityQueryWindowKey(entity, { page: 1 })).toEqual([
      "crud",
      "/api/task",
      "default",
      "queryWindow",
      { page: 1 }
    ])
  })
})
