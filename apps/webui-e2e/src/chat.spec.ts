import { expect, test } from "@playwright/test"

test.describe("AI 对话", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("创建会话→发送消息→收到回复→重命名→删除", async ({ page }) => {
    // 进入 AI 对话页面
    await page.goto("/chat")

    // 创建新会话
    await page.getByRole("button", { name: /新建|新对话/ }).click()
    await expect(page.getByPlaceholder(/输入|发送/)).toBeVisible()

    // 发送消息
    await page.getByPlaceholder(/输入|发送/).fill("你好，请介绍一下自己")
    await page.getByRole("button", { name: /发送/ }).click()

    // 等待 AI 回复（流式输出，最多 30 秒）
    await expect(page.locator('[data-role="assistant"]').first()).toBeVisible({
      timeout: 30000
    })

    // 重命名会话
    const sessionItem = page.locator('[data-testid="session-item"]').first()
    await sessionItem.hover()
    await sessionItem.getByRole("button", { name: /重命名|编辑/ }).click()
    await page.getByRole("textbox").fill("E2E测试对话")
    await page.keyboard.press("Enter")
    await expect(page.getByText("E2E测试对话")).toBeVisible()

    // 删除会话
    await sessionItem.hover()
    await sessionItem.getByRole("button", { name: /删除/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText("E2E测试对话")).not.toBeVisible()
  })
})
