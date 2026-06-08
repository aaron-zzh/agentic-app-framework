import Link from "next/link"

import BirdsPage from "./BirdsPage"
import BoxPage from "./BoxPage"

export default async function Page() {
  return (
    <>
      <h1>示例: threejs</h1>
      <BoxPage />
      <BirdsPage />
      <div className="flex flex-col gap-2 p-6">
        <Link href="/examples/threejs/video" className="text-primary underline">
          → WebGL 视频纹理示例
        </Link>
        <Link href="/examples/threejs/waves" className="text-primary underline">
          → 粒子波浪示例
        </Link>
      </div>
    </>
  )
}
