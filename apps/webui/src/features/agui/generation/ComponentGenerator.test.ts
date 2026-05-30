/**
 * ComponentGenerator.ts 单元测试——验证意图解析和配置生成
 */

import { describe, expect, it } from "vitest"
import { ComponentGeneratorImpl } from "./ComponentGenerator"

describe("ComponentGeneratorImpl", () => {
  const generator = new ComponentGeneratorImpl()

  describe("parseIntent", () => {
    it("应识别列表视图意图", () => {
      const intent = generator.parseIntent("创建一个用户列表")

      expect(intent.type).toBe("generate-view")
      expect(intent.entity).toBeDefined()
    })

    it("应识别表单视图意图", () => {
      const intent = generator.parseIntent("生成一个编辑表单")

      expect(intent.type).toBe("generate-form")
    })

    it("应识别看板视图意图", () => {
      const intent = generator.parseIntent("创建任务看板")

      expect(intent.type).toBe("generate-kanban")
    })

    it("应识别仪表盘意图", () => {
      const intent = generator.parseIntent("生成 dashboard")

      expect(intent.type).toBe("generate-dashboard")
    })

    it("应提取特性关键词", () => {
      const intent = generator.parseIntent("创建一个支持搜索和分页的列表")

      expect(intent.features).toContain("search")
      expect(intent.features).toContain("paginate")
    })

    it("默认应为 generate-view", () => {
      const intent = generator.parseIntent("随便生成一个")

      expect(intent.type).toBe("generate-view")
    })
  })
})
