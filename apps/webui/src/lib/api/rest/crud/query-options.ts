/**
 * 标准 CRUD Query Options 工厂。
 */

import { queryOptions } from "@tanstack/react-query"
import type { ListParams } from "@/lib/api/types"
import {
  fetchCrudMeta,
  fetchList,
  fetchQueryWindow,
  fetchRecord,
  type CrudDetailParams,
  type CrudId,
  type CrudQueryWindowParams,
  type CrudRecord,
  type CrudResource
} from "./client"

/**
 * React Query v5 Query Options 对象模式说明：
 *
 * v5 重大变更：所有函数现在只接收一个 Query Options 对象，而非多个参数
 * 这个对象包含创建查询所需的所有选项配置
 *
 * 优势：
 * 1. 类型安全：queryOptions() 提供完整的类型推断和约束
 * 2. 可复用性：options 对象可在多处使用（预取、SSR、组件等）
 * 3. 一致性：统一的 API 设计，减少学习成本
 * 4. 扩展性：新增选项时无需修改函数签名
 */

export function crudKey(resource: CrudResource): readonly unknown[] {
  return ["crud", resource.apiPath]
}

export function crudListKey(
  resource: CrudResource,
  params: ListParams = {}
): readonly unknown[] {
  return [...crudKey(resource), "list", params]
}

export function crudQueryWindowKey(
  resource: CrudResource,
  params: CrudQueryWindowParams = {}
): readonly unknown[] {
  return [...crudKey(resource), "queryWindow", params]
}

export function crudDetailKey(
  resource: CrudResource,
  id: CrudId | undefined,
  params: CrudDetailParams = {}
): readonly unknown[] {
  return [...crudKey(resource), "detail", { id, ...params }]
}

export function crudMetaKey(resource: CrudResource): readonly unknown[] {
  return [...crudKey(resource), "meta"]
}

export function crudListOptions<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: ListParams = {}
) {
  return queryOptions({
    queryKey: crudListKey(resource, params),
    queryFn: () => fetchList<TRecord>(resource, params)
  })
}

export function crudQueryWindowOptions<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: CrudQueryWindowParams = {}
) {
  return queryOptions({
    queryKey: crudQueryWindowKey(resource, params),
    queryFn: () => fetchQueryWindow<TRecord>(resource, params)
  })
}

export function crudDetailOptions<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  id: CrudId | undefined,
  params: CrudDetailParams = {}
) {
  return queryOptions({
    queryKey: crudDetailKey(resource, id, params),
    queryFn: () => fetchRecord<TRecord>(resource, id ?? "", params),
    enabled: id !== undefined && id !== null && id !== ""
  })
}

export function crudMetaOptions<TMeta = CrudRecord>(resource: CrudResource) {
  return queryOptions({
    queryKey: crudMetaKey(resource),
    queryFn: () => fetchCrudMeta<TMeta>(resource)
  })
}
