import { redirect } from "next/navigation"
import { paths } from "@/lib/constants/paths"

export default function WorkspacePage() {
  redirect(paths.workspace.dashboard)
}
