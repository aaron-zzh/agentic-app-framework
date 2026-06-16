/**
 * 3D 素材预览页——全屏 ModelViewer + 元数据侧栏
 * @author AaronZZH & Kiro
 */

"use client"

import { ArrowLeft, Download } from "lucide-react"
import Link from "next/link"
import { use } from "react"
import { Button } from "@/components/ui/button"
import { ModelViewer } from "@/features/aigc/three/ModelViewer"
import { useMediaAssetDetail } from "@/lib/queries/use-media-assets"

export default function AssetPreviewPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params)
  const { data: asset } = useMediaAssetDetail(Number(id))

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-3 border-b px-4 py-3">
        <Button
          variant="ghost"
          size="icon"
          className="size-8"
          nativeButton={false}
          render={<Link href="/aigc/assets" />}
        >
          <ArrowLeft className="size-4" />
        </Button>
        <h1 className="font-semibold">{asset?.name ?? "3D 预览"}</h1>
        {asset && (
          <Button
            variant="outline"
            size="sm"
            className="ml-auto"
            onClick={() => window.open(asset.url, "_blank")}
          >
            <Download className="mr-1.5 size-3.5" />
            下载
          </Button>
        )}
      </div>
      <div className="flex-1">
        {asset ? (
          <ModelViewer modelUrl={asset.url} className="size-full" />
        ) : (
          <div className="flex size-full items-center justify-center text-muted-foreground">
            加载中...
          </div>
        )}
      </div>
    </div>
  )
}
