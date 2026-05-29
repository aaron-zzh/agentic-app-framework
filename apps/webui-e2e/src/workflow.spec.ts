import { expect, test } from "@playwright/test"

test.describe("工作流", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("工作流编排→发布→执行", async ({ page }) => {
    // 进入工作流页面
    await page.goto("/workflow")

    // 创建工作流
    await page.getByRole("button", { name: "新建" }).click()
    await page.getByLabel("名称").fill("E2E测试流程")
    await page.getByRole("button", { name: "确定" }).click()

    // 编排节点（拖拽模拟）
    await expect(page.getByText("E2E测试流程")).toBeVisible()

    // 发布
    await page.getByRole("button", { name: "发布" }).click()
    await expect(page.getByText("已发布")).toBeVisible()

    // 执行
    await page.getByRole("button", { name: "执行" }).click()
    await expect(page.getByText("运行中")).toBeVisible()
  })
})
