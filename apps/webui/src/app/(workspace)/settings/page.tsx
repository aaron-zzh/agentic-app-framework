/**
 * 设置首页——重定向到通知设置
 * @author AaronZZH & Kiro
 */

import { redirect } from "next/navigation"

export default function SettingsPage() {
  redirect("/settings/notifications")
}
