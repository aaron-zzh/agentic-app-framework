/**
 * 数据归档 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export const archiveApi = {
  /** 归档记录 */
  archive: (entity: string, id: string) =>
    backendApi.post<void>(`/${entity}/${id}/archive`),

  /** 恢复到活跃 */
  unarchive: (entity: string, id: string) =>
    backendApi.post<void>(`/${entity}/${id}/unarchive`)
}
