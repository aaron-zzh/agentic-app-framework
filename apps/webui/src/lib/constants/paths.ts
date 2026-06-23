/**
 * 路由常量集中定义——路径变更只改一处
 * @author AaronZZH & Kiro
 *
 * 注意：(workspace) 是 Next.js 路由组，括号不计入 URL。
 * 实际路由：/dashboard、/notifications、/[module]、/[module]/[id]
 *
 * @example
 * ```ts
 * import { paths } from "@/lib/constants/paths"
 * <Link href={paths.workspace.module("document")} />
 * <Link href={paths.workspace.record("document", "123")} />
 * ```
 */

export const paths = {
  root: "/",
  feedback: "/feedback",
  auth: {
    login: "/login",
    register: "/register",
    forgotPassword: "/forgot-password",
    oauthCallback: "/login/oauth-callback"
  },
  workspace: {
    root: "/dashboard",
    dashboard: "/dashboard",
    module: (slug: string) => `/module/${slug}`,
    record: (slug: string, id: string) => `/module/${slug}/${id}`,
    settings: "/settings",
    settingsProfile: "/settings/profile",
    settingsCredits: "/settings/credits",
    settingsPricing: "/settings/pricing",
    settingsNotifications: "/settings/notifications",
    notifications: "/notifications",
    todos: "/todos",
    trash: "/trash"
  },
  aigc: {
    root: "/aigc",
    assets: "/aigc/assets",
    video: "/aigc/video"
  },
  studio: {
    welcome: "/studio/welcome",
    me: "/studio/me",
    mePricing: "/studio/me/membership",
    meCredits: "/studio/me/credits",
    meAccount: "/studio/me/account"
  },
  admin: {
    demo: "/admin/demo"
  },
  docs: {
    root: "/docs",
    new: "/docs/new",
    edit: (id: number | string) => `/docs/${id}/edit`,
    public: (id: number | string) => `/docs-public/${id}`
  }
}
