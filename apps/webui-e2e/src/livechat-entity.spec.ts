import { expect, test } from "@playwright/test"

test.describe("Livechat 客服对话", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("打开对话面板→发送消息→收到回复", async ({ page }) => {
    // 打开 livechat 面板（通常是右下角浮动按钮或侧边栏）
    const chatTrigger = page.getByRole("button", { name: /对话|聊天|Chat/ }).first()
    if (await chatTrigger.isVisible()) {
      await chatTrigger.click()
    } else {
      await page.goto("/chat")
    }

    // 等待对话输入框出现
    const input = page.getByPlaceholder(/输入|发送|消息/)
    await expect(input).toBeVisible({ timeout: 5000 })

    // 发送消息
    await input.fill("你好，请帮我查一下订单状态")
    await page.keyboard.press("Enter")

    // 等待回复（bot 或 AI）
    await expect(
      page.locator('[data-role="assistant"], [data-sender="bot"], [data-sender="ai"]').first()
    ).toBeVisible({ timeout: 15000 })
  })

  test("斜杠命令应弹出命令面板", async ({ page }) => {
    await page.goto("/chat")
    const input = page.getByPlaceholder(/输入|发送|消息/)
    await expect(input).toBeVisible({ timeout: 5000 })

    // 输入 /
    await input.fill("/")

    // 应弹出命令列表
    await expect(page.getByText(/搜索|创建|帮助/).first()).toBeVisible({ timeout: 3000 })
  })
})

test.describe("通用实体列表和表单", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("实体列表应支持搜索和分页", async ({ page }) => {
    await page.goto("/admin/users")
    await expect(page.getByRole("table")).toBeVisible()

    // 搜索
    const searchInput = page.getByPlaceholder(/搜索/)
    if (await searchInput.isVisible()) {
      await searchInput.fill("admin")
      await page.keyboard.press("Enter")
      await expect(page.getByText("admin")).toBeVisible()
    }

    // 分页（如果有）
    const nextPage = page.getByRole("button", { name: /下一页|>/ })
    if (await nextPage.isVisible()) {
      await nextPage.click()
      await expect(page.getByRole("table")).toBeVisible()
    }
  })

  test("实体表单应支持字段验证", async ({ page }) => {
    await page.goto("/admin/users")
    await page.getByRole("button", { name: /新建|创建/ }).click()

    // 不填必填字段直接提交
    await page.getByRole("button", { name: /确定|保存/ }).click()

    // 应显示验证错误
    await expect(page.getByText(/必填|不能为空|required/).first()).toBeVisible()
  })
})
