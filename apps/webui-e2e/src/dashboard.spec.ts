import { expect, test } from "@playwright/test"

test.describe("Dashboard 仪表盘", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("创建仪表盘→添加组件→预览→删除", async ({ page }) => {
    // 进入仪表盘页面
    await page.goto("/dashboard")

    // 创建仪表盘
    await page.getByRole("button", { name: /新建|创建/ }).click()
    await page.getByLabel("名称").fill("E2E测试仪表盘")
    await page.getByRole("button", { name: /确定|创建/ }).click()
    await expect(page.getByText("E2E测试仪表盘")).toBeVisible()

    // 添加组件
    await page.getByRole("button", { name: /添加组件|添加/ }).click()
    await page.getByText(/计数器|counter/).first().click()
    await expect(page.locator('[data-testid="widget"]').first()).toBeVisible()

    // 保存布局
    await page.getByRole("button", { name: /保存/ }).click()
    await expect(page.getByText(/已保存|保存成功/)).toBeVisible()

    // 删除仪表盘
    await page.getByRole("button", { name: /设置|更多/ }).click()
    await page.getByRole("menuitem", { name: /删除/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText("E2E测试仪表盘")).not.toBeVisible()
  })
})
