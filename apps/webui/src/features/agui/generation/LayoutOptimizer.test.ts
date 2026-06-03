/**
 * LayoutOptimizer.ts 单元测试——验证布局优化算法
 */

import { describe, expect, it } from "vitest"
import type { FieldDef } from "@/lib/types/entity"
import { LayoutOptimizer as optimizer } from "./LayoutOptimizer"

describe("LayoutOptimizer", () => {
  const mockFields: FieldDef[] = [
    { name: "id", label: "ID", type: "number", required: true },
    { name: "name", label: "名称", type: "string", required: true },
    { name: "email", label: "邮箱", type: "string", required: true },
    { name: "status", label: "状态", type: "enum", required: false },
    { name: "description", label: "描述", type: "text", required: false },
    { name: "createdAt", label: "创建时间", type: "datetime", required: false },
    { name: "avatar", label: "头像", type: "image", required: false }
  ]

  describe("generateProposals", () => {
    it("应返回多个布局方案", () => {
      const proposals = optimizer.generateProposals(mockFields)

      expect(proposals.length).toBeGreaterThanOrEqual(1)
      expect(proposals.length).toBeLessThanOrEqual(3)
    })

    it("方案应按分数降序排列", () => {
      const proposals = optimizer.generateProposals(mockFields)

      for (let i = 1; i < proposals.length; i++) {
        expect(proposals[i - 1].score).toBeGreaterThanOrEqual(proposals[i].score)
      }
    })

    it("字段数 > 5 时应包含 Tab 布局方案", () => {
      const proposals = optimizer.generateProposals(mockFields)
      const hasTabbed = proposals.some((p) => p.layout.type === "tabs")

      expect(hasTabbed).toBe(true)
    })

    it("maxProposals 应限制返回数量", () => {
      const proposals = optimizer.generateProposals(mockFields, { maxProposals: 1 })

      expect(proposals).toHaveLength(1)
    })
  })

  describe("optimizeListColumns", () => {
    it("应返回不超过 maxColumns 的列", () => {
      const columns = optimizer.optimizeListColumns(mockFields, 4)

      expect(columns.length).toBeLessThanOrEqual(4)
    })

    it("应优先选择重要字段", () => {
      const columns = optimizer.optimizeListColumns(mockFields, 3)

      // 必填字段（name, email）应优先
      expect(columns).toContain("name")
    })
  })

  describe("generateResponsive", () => {
    it("应生成响应式配置", () => {
      const responsive = optimizer.generateResponsive(mockFields)

      expect(responsive.desktopColumns).toBeGreaterThan(0)
      expect(responsive.tabletColumns).toBeGreaterThan(0)
      expect(responsive.mobileColumns).toBeGreaterThan(0)
    })

    it("桌面端列数应 >= 平板端 >= 手机端", () => {
      const responsive = optimizer.generateResponsive(mockFields)

      expect(responsive.desktopColumns).toBeGreaterThanOrEqual(responsive.tabletColumns)
      expect(responsive.tabletColumns).toBeGreaterThanOrEqual(responsive.mobileColumns)
    })
  })
})
