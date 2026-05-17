/**
 * 路由常量集中定义——路径变更只改一处
 * @author AaronZZH & Kiro
 *
 * @example
 * ```ts
 * import { paths } from "@/lib/constants/paths"
 * <Link href={paths.workspace.module("document")} />
 * <Link href={paths.workspace.record("document", "123")} />
 * ```
 */

const ROOTS = {
  AUTH: "/auth",
  WORKSPACE: "/workspace"
}

export const paths = {
  root: "/",
  auth: {
    login: `${ROOTS.AUTH}/login`,
    register: `${ROOTS.AUTH}/register`
  },
  workspace: {
    root: ROOTS.WORKSPACE,
    dashboard: `${ROOTS.WORKSPACE}/dashboard`,
    module: (slug: string) => `${ROOTS.WORKSPACE}/${slug}`,
    record: (slug: string, id: string) => `${ROOTS.WORKSPACE}/${slug}/${id}`,
    settings: `${ROOTS.WORKSPACE}/settings`,
    notifications: `${ROOTS.WORKSPACE}/notifications`,
    todos: `${ROOTS.WORKSPACE}/todos`
  }
}
