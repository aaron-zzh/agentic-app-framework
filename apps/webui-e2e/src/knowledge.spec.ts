import { expect, test } from "@playwright/test"
import path from "node:path"

test.describe("知识库管理", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("创建知识库→上传文档→查看状态→搜索→删除", async ({ page }) => {
    // 进入知识库页面
    await page.goto("/knowledge")

    // 创建知识库
    await page.getByRole("button", { name: /新建|创建/ }).click()
    await page.getByLabel("名称").fill("E2E测试知识库")
    await page.getByLabel("描述").fill("自动化测试用知识库")
    await page.getByRole("button", { name: /确定|创建/ }).click()
    await expect(page.getByText("E2E测试知识库")).toBeVisible()

    // 进入知识库详情
    await page.getByText("E2E测试知识库").click()

    // 上传文档
    const fileInput = page.locator('input[type="file"]')
    await fileInput.setInputFiles({
      name: "test-doc.md",
      mimeType: "text/markdown",
      buffer: Buffer.from("# 测试文档\n\n这是一个用于 E2E 测试的知识库文档。包含一些测试内容。")
    })

    // 等待处理完成
    await expect(page.getByText(/完成|已处理|COMPLETED/)).toBeVisible({ timeout: 30000 })

    // 搜索
    const searchInput = page.getByPlaceholder(/搜索|查询/)
    if (await searchInput.isVisible()) {
      await searchInput.fill("测试文档")
      await page.keyboard.press("Enter")
      await expect(page.getByText(/测试/)).toBeVisible()
    }

    // 返回列表并删除
    await page.goto("/knowledge")
    const row = page.getByText("E2E测试知识库").locator("..")
    await row.getByRole("button", { name: /删除/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText("E2E测试知识库")).not.toBeVisible()
  })
})
