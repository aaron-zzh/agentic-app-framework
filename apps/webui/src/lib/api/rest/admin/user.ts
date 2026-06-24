/**
 * 用户管理 API 客户端
 * @author AaronZZH & Kiro
 */

import { buildApiUrl } from "../../config"
import { backendApi } from "../backend-client"
import { buildQuery, type ListParams, type PageResult } from "../entity/crud"

export interface UserVO {
  id: number
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  createTime: string
  updateTime: string
}

export interface UserListParams extends ListParams {
  username?: string
  nickname?: string
  status?: number
}

export interface ImportResult {
  successCount: number
  failureCount: number
  failureMessages: string[]
}

export const adminUserApi = {
  list: (params: UserListParams = {}) =>
    backendApi.get<PageResult<UserVO>>(`/system/users${buildQuery(params)}`),

  import: (file: File, updateSupport = false) => {
    const form = new FormData()
    form.append("file", file)
    form.append("updateSupport", String(updateSupport))
    return backendApi.post<ImportResult>("/system/users/import", form, {
      headers: { "Content-Type": undefined }
    })
  },

  downloadTemplate: () => {
    window.open(buildApiUrl("/system/users/import/template"), "_blank")
  }
}
