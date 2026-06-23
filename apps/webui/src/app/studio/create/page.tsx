/**
 * /studio/create 默认重定向到图像创作 sub-tab
 */

import { redirect } from "next/navigation"

export default function StudioCreatePage() {
  redirect("/studio/create/image")
}
