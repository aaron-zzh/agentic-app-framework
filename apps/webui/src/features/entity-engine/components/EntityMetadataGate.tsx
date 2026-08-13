"use client"

import type { ReactNode } from "react"
import { useCallback, useEffect, useState } from "react"
import { SplashScreen } from "@/components/common/SplashScreen"
import { Button } from "@/components/ui/button"
import { entityRegistry } from "@/features/entity-engine/lib/registry"
import { parseEntityDefBootstrap } from "@/features/entity-engine/lib/server-entity-def"
import { useEntityBootstrap } from "@/lib/api/rest/entity"

interface EntityMetadataGateProps {
  children: ReactNode
  enabled?: boolean
}

/** 共享实体元数据启动门禁，确保所有 EntityRegistry 消费者看到完整注册表。 */
export function EntityMetadataGate({ children, enabled = true }: EntityMetadataGateProps) {
  const [metadataReady, setMetadataReady] = useState(false)
  const [metadataError, setMetadataError] = useState<string | null>(null)
  const { data: bootstrap, isError, isLoading, refetch } = useEntityBootstrap({ enabled })

  useEffect(() => {
    if (!bootstrap) return

    const { definitions, resources, errors } = parseEntityDefBootstrap(bootstrap)
    if (errors.length > 0) {
      setMetadataReady(false)
      setMetadataError(`实体元数据不可用：${errors.join("；")}`)
      return
    }

    entityRegistry.replaceAll(definitions, resources)
    setMetadataError(null)
    setMetadataReady(true)
  }, [bootstrap])

  const retry = useCallback(() => {
    setMetadataReady(false)
    setMetadataError(null)
    void refetch()
  }, [refetch])

  const bootstrapError = metadataError ?? (isError ? "实体元数据加载失败" : null)
  if (!enabled || isLoading || (!metadataReady && !bootstrapError)) {
    return <SplashScreen />
  }
  if (bootstrapError) {
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="max-w-md space-y-3 text-center">
          <h1 className="font-semibold text-lg">工作区不可用</h1>
          <p className="text-muted-foreground text-sm">{bootstrapError}</p>
          <Button type="button" onClick={retry}>
            重试
          </Button>
        </div>
      </div>
    )
  }

  return <>{children}</>
}
