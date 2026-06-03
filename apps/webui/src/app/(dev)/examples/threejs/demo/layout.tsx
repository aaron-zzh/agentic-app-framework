import type { ReactNode } from "react"

import { SceneLayout } from "@/components/r3f/SceneLayout"

export const metadata = {
  title: "Next.js + Three.js",
  description: "A minimal starter for Nextjs + React-three-fiber and Threejs."
}

export default function PageLayout({ children }: { children: ReactNode }) {
  return <SceneLayout>{children}</SceneLayout>
}
