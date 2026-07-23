"use client"

import { entityRegistry } from "@/features/entity-engine/lib/registry"
import { EntityCreateView } from "./entity-create-view"
import { EntityListView } from "./entity-list-view"
import { EntityRecordView } from "./entity-record-view"

type EntityModuleRouteProps =
  | { kind: "list"; module: string; view?: string }
  | { kind: "create"; module: string }
  | { kind: "record"; module: string; recordId: string; queryToken?: string }

/** 在工作区元数据启动门禁完成后解析并渲染通用实体路由。 */
export function EntityModuleRoute(props: EntityModuleRouteProps) {
  const entity = entityRegistry.get(props.module)
  if (!entity) {
    return <EntityDefinitionUnavailable module={props.module} />
  }

  switch (props.kind) {
    case "list":
      return <EntityListView entity={entity} view={props.view} />
    case "create":
      return entity.access?.create === false ? (
        <EntityDefinitionUnavailable module={props.module} />
      ) : (
        <EntityCreateView entity={entity} />
      )
    case "record":
      return (
        <EntityRecordView entity={entity} recordId={props.recordId} queryToken={props.queryToken} />
      )
  }
}

function EntityDefinitionUnavailable({ module }: { module: string }) {
  return (
    <div className="flex flex-1 items-center justify-center p-6">
      <div className="max-w-md space-y-2 text-center">
        <h1 className="font-semibold text-lg">实体不可用</h1>
        <p className="text-muted-foreground text-sm">未找到“{module}”的可用实体元数据。</p>
      </div>
    </div>
  )
}
