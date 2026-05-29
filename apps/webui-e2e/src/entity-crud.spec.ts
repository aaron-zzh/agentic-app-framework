import { expect, test } from "@playwright/test"

test.describe("实体 CRUD", () => {
  test.beforeEach(async ({ page }) => {
    // 登录
    await page.goto("/login")
    await page.getByLabel("用户名").fill("admin")
    await page.getByLabel("密码").fill("admin123")
    await page.getByRole("button", { name: "登录" }).click()
    await expect(page).toHaveURL("/")
  })

  test("实体列表→新建→编辑→删除", async ({ page }) => {
    // 进入列表页
    await page.goto("/system/dept")
    await expect(page.getByRole("table")).toBeVisible()

    // 新建
    await page.getByRole("button", { name: "新建" }).click()
    await page.getByLabel("名称").fill("E2E测试部门")
    await page.getByRole("button", { name: "确定" }).click()
    await expect(page.getByText("E2E测试部门")).toBeVisible()

    // 编辑
    await page.getByText("E2E测试部门").click()
    await page.getByRole("button", { name: "编辑" }).click()
    await page.getByLabel("名称").fill("E2E修改部门")
    await page.getByRole("button", { name: "确定" }).click()
    await expect(page.getByText("E2E修改部门")).toBeVisible()

    // 删除
    await page.getByText("E2E修改部门").click()
    await page.getByRole("button", { name: "删除" }).click()
    await page.getByRole("button", { name: "确认" }).click()
    await expect(page.getByText("E2E修改部门")).not.toBeVisible()
  })
})
