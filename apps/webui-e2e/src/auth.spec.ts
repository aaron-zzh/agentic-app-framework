import { expect, test } from "@playwright/test"

test.describe("认证流程", () => {
  test("登录成功后跳转首页", async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("注册新用户", async ({ page }) => {
    await page.goto("/register")
    await page.getByLabel("邮箱").fill("test@example.com")
    await page.getByLabel("密码").fill("Test123456!")
    await page.getByLabel("确认密码").fill("Test123456!")
    await page.getByRole("button", { name: "注册" }).click()
    await expect(page.getByText("注册成功")).toBeVisible()
  })

  test("忘记密码发送重置邮件", async ({ page }) => {
    await page.goto("/forgot-password")
    await page.getByLabel("邮箱").fill("test@example.com")
    await page.getByRole("button", { name: "发送" }).click()
    await expect(page.getByText("已发送")).toBeVisible()
  })
})
