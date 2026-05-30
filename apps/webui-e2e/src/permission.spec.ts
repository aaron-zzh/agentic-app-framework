import { expect, test } from "@playwright/test"

test.describe("权限管理", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("创建角色→分配权限→验证", async ({ page }) => {
    // 进入角色管理
    await page.goto("/admin/roles")

    // 创建角色
    await page.getByRole("button", { name: /新建|创建/ }).click()
    await page.getByLabel("角色名称").fill("E2E测试角色")
    await page.getByLabel("角色编码").fill("e2e_test_role")
    await page.getByRole("button", { name: /确定|保存/ }).click()
    await expect(page.getByText("E2E测试角色")).toBeVisible()

    // 分配权限
    await page.getByText("E2E测试角色").click()
    await page.getByRole("tab", { name: /权限/ }).click()
    const firstPermission = page.getByRole("checkbox").first()
    await firstPermission.check()
    await page.getByRole("button", { name: /保存/ }).click()
    await expect(page.getByText(/保存成功|已更新/)).toBeVisible()

    // 清理：删除角色
    await page.goto("/admin/roles")
    await page.getByText("E2E测试角色").click()
    await page.getByRole("button", { name: /删除/ }).click()
    await page.getByRole("button", { name: /确认|确定/ }).click()
    await expect(page.getByText("E2E测试角色")).not.toBeVisible()
  })
})
