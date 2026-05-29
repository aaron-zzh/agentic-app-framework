import { expect, test } from "@playwright/test"

test.describe("AIGC 图片生成", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("图片生成→保存素材库", async ({ page }) => {
    // 进入 AIGC 页面
    await page.goto("/aigc/image")

    // 输入提示词并生成
    await page.getByPlaceholder("描述").fill("一只可爱的猫咪")
    await page.getByRole("button", { name: "生成" }).click()

    // 等待生成完成
    await expect(page.getByRole("img", { name: /生成/ })).toBeVisible({
      timeout: 30000,
    })

    // 保存到素材库
    await page.getByRole("button", { name: "保存" }).click()
    await expect(page.getByText("已保存")).toBeVisible()
  })
})
