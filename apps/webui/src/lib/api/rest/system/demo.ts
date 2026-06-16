/**
 * 演示模式 API 客户端
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export const demoApi = {
  /** 加载演示数据 */
  load: () => backendApi.post<void>("/system/demo/load"),

  /** 清理演示数据 */
  clean: () => backendApi.delete<void>("/system/demo/clean")
}
