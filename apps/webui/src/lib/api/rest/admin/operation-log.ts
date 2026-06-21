/**
 * 操作日志 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

export interface OperationLogVO {
  id: number
  userId: number
  username: string
  module: string
  type: string
  description: string
  bizNo: string
  requestMethod: string
  requestUrl: string
  durationMs: number
  success: boolean
  errorMessage: string
  createTime: string
}

export interface OperationLogListParams extends ListParams {
  module?: string
  type?: string
  userId?: string
  startTime?: string
  endTime?: string
}

export const operationLogApi = {
  list: (params: OperationLogListParams = {}) =>
    backendApi.get<PageResult<OperationLogVO>>(`/operation-logs${buildQuery(params)}`)
}
