/**
 * 卡片内嵌 3D 预览——基于共享 Canvas 的 View，支持多实例
 * @author AaronZZH & Kiro
 */

"use client"

import { Center, useGLTF } from "@react-three/drei"
import dynamic from "next/dynamic"
import { Suspense } from "react"

const View = dynamic(() => import("@/components/r3f/helpers/View").then((m) => m.View), {
  ssr: false,
  loading: () => <div className="size-full animate-pulse bg-muted" />
})

function Model({ url }: { url: string }) {
  const { scene } = useGLTF(url)
  return (
    <Center>
      <primitive object={scene} />
    </Center>
  )
}

interface Model3DPreviewProps {
  url: string
  className?: string
}

export function Model3DPreview({ url, className }: Model3DPreviewProps) {
  return (
    <View orbit className={className}>
      <ambientLight intensity={0.8} />
      <pointLight position={[5, 5, 5]} intensity={2} />
      <Suspense fallback={null}>
        <Model url={url} />
      </Suspense>
    </View>
  )
}
