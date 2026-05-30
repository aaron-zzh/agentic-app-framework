/**
 * 设置首页——重定向到个人资料
 * @author AaronZZH & Kiro
 */

import { redirect } from "next/navigation"

export default function SettingsPage() {
  redirect("/settings/profile")
}
