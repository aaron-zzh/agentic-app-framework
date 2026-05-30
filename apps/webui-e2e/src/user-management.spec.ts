import { expect, test } from "@playwright/test"

test.describe("用户管理", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("用户列表→新建→编辑→禁用→删除", async ({ page }) => {
    await page.goto("/admin/users")
    await expect(page.getByRole("table")).toBeVisible()

    // 新建用户
    await page.getByRole("button", { name: /新建|创建/ }).click()
    await page.getByLabel("用户名").fill("e2e_test_user")
    await page.getByLabel("邮箱").fill("e2e@test.com")
    await page.getByLabel("密码").fill("Test123456!")
    await page.getByRole("button", { name: /确定|保存/ }).click()
    await expect(page.getByText("e2e_test_user")).toBeVisible()

    // 编辑用户
    await page.getByText("e2e_test_user").click()
    await page.getByRole("button", { name: /编辑/ }).click()
    await page.getByLabel("昵称").fill("E2E测试用户")
    await page.getByRole("button", { name: /确定|保存/ }).click()
    await expect(page.getByText("E2E测试用户")).toBeVisible()

    // 禁用用户
    await page.getByText("e2e_test_user").click()
    await page.getByRole("button", { name: /禁用|停用/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText(/已禁用|已停用/)).toBeVisible()

    // 删除用户
    await page.getByText("e2e_test_user").click()
    await page.getByRole("button", { name: /删除/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText("e2e_test_user")).not.toBeVisible()
  })
})
