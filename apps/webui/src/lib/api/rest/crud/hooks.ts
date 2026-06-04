/**
 * 标准 CRUD React Query hooks。
 */

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import type { ListParams, PageResult } from "@/lib/api/types"
import {
  createRecord,
  deleteRecord,
  deleteRecords,
  updateRecord,
  type CrudData,
  type CrudDetailParams,
  type CrudId,
  type CrudQueryWindowParams,
  type CrudRecord,
  type CrudResource
} from "./client"
import {
  crudDetailKey,
  crudDetailOptions,
  crudKey,
  crudListOptions,
  crudMetaOptions,
  crudQueryWindowOptions,
} from "./query-options"

export interface CrudQueryOptions {
  enabled?: boolean
}

export interface CrudMutationOptions {
  optimistic?: boolean
}

interface UpdateVariables<TUpdate> {
  id: CrudId
  data: TUpdate
}

interface DeleteVariables {
  id: CrudId
}

interface DeleteManyVariables {
  ids: CrudId[]
}

interface OptimisticContext<TRecord> {
  previousDetail?: TRecord
}

export function useCrudList<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: ListParams = {},
  options: CrudQueryOptions = {}
){
  return useQuery({
    ...crudListOptions<TRecord>(resource, params),
    enabled: options.enabled,
    placeholderData: keepPreviousData
  })
}

export function useCrudQueryWindow<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  params: CrudQueryWindowParams = {},
  options: CrudQueryOptions = {}
) {
  return useQuery({
    ...crudQueryWindowOptions<TRecord>(resource, params),
    enabled: options.enabled,
    placeholderData: keepPreviousData
  })
}

export function useCrudRecord<TRecord extends CrudRecord = CrudRecord>(
  resource: CrudResource<TRecord>,
  id: CrudId | undefined,
  params: CrudDetailParams = {},
  options: CrudQueryOptions = {}
) {
  const detailOptions = crudDetailOptions<TRecord>(resource, id, params)
  return useQuery({
    ...detailOptions,
    enabled: detailOptions.enabled && options.enabled !== false
  })
}

export function useCrudMeta<TMeta = CrudRecord>(
  resource: CrudResource,
  options: CrudQueryOptions = {}
) {
  return useQuery({
    ...crudMetaOptions<TMeta>(resource),
    enabled: options.enabled
  })
}

export function useCrudCreate<
  TRecord extends CrudRecord = CrudRecord,
  TCreate extends CrudData = CrudData
>(resource: CrudResource<TRecord>) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (data: TCreate) => createRecord<TRecord, TCreate>(resource, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: crudKey(resource) })
    }
  })
}

export function useCrudUpdate<
  TRecord extends CrudRecord = CrudRecord,
  TUpdate extends CrudData = CrudData
>(resource: CrudResource<TRecord>, options: CrudMutationOptions = {}) {
  const queryClient = useQueryClient()

  return useMutation<TRecord, Error, UpdateVariables<TUpdate>, OptimisticContext<TRecord>>({
    mutationFn: ({ id, data }) => updateRecord<TRecord, TUpdate>(resource, id, data),
    onMutate: async ({ id, data }) => {
      if (!options.optimistic) return {}

      const detailKey = crudDetailKey(resource, id, { fieldSet: "detail" })
      await queryClient.cancelQueries({ queryKey: detailKey })
      const previousDetail = queryClient.getQueryData<TRecord>(detailKey)

      queryClient.setQueryData<TRecord>(detailKey, (old) => {
        if (!old) return old
        return { ...old, ...data } as TRecord
      })

      return { previousDetail }
    },
    onError: (_error, variables, context) => {
      if (!context?.previousDetail) return
      queryClient.setQueryData(
        crudDetailKey(resource, variables.id, { fieldSet: "detail" }),
        context.previousDetail
      )
    },
    onSuccess: (record, variables) => {
      queryClient.setQueryData(crudDetailKey(resource, variables.id, { fieldSet: "detail" }), record)
    },
    onSettled: (_data, _error, variables) => {
      queryClient.invalidateQueries({ queryKey: crudKey(resource) })
      queryClient.invalidateQueries({ queryKey: crudDetailKey(resource, variables.id) })
    }
  })
}

export function useCrudDelete(resource: CrudResource) {
  const queryClient = useQueryClient()

  return useMutation<void, Error, DeleteVariables>({
    mutationFn: ({ id }) => deleteRecord(resource, id),
    onSuccess: (_data, variables) => {
      queryClient.removeQueries({ queryKey: crudDetailKey(resource, variables.id) })
      queryClient.invalidateQueries({ queryKey: crudKey(resource) })
    }
  })
}

export function useCrudDeleteMany(resource: CrudResource) {
  const queryClient = useQueryClient()

  return useMutation<void, Error, DeleteManyVariables>({
    mutationFn: ({ ids }) => deleteRecords(resource, ids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: crudKey(resource) })
    }
  })
}

export function getRecordList<TRecord>(page: PageResult<TRecord> | undefined): TRecord[] {
  return page?.list ?? []
}
