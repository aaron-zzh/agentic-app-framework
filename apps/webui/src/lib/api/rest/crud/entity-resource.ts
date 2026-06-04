import type { EntityDef } from "@/lib/types/entity"
import type { CrudRecord, CrudResource } from "./client"

/**
 * 动态实体引擎的过渡适配器。
 *
 * 新业务模块优先使用 endpoints.ts 中登记的 crudResources；这里仅用于现有 EntityDef
 * TODO 运行时元数据场景。后续当实体定义统一落到资源注册表或后端元数据生成资源后，可逐步移除。
 */
export function fromEntityDef<TRecord = CrudRecord>(entity: EntityDef): CrudResource<TRecord> {
  return {
    apiPath: entity.apiPath
  }
}
