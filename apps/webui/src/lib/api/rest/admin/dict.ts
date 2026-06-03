/**
 * 字典数据 API
 * @author AaronZZH & Kiro
 */

import { request } from "@/lib/api/rest/entity/crud"

export interface DictDataVO {
  id: number
  dictType: string
  label: string
  value: string
  sort: number
  status: number
  /** 标签颜色（default/primary/secondary/error/warning/info/success） */
  colorType: string
  cssClass: string
  remark: string
}

export const dictApi = {
  /** 按字典类型查询（按需加载） */
  getByType: (dictType: string) => request<DictDataVO[]>(`/system/dict-data/type/${dictType}`),

  /** 一次性拉取全部启用字典（扁平列表，前端按 dictType 分组） */
  listAll: () => request<DictDataVO[]>("/system/dict-data/list-all-simple")
}
