/**
 * useDict——字典数据查询 Hook
 *
 * 参考 arts-admin useDict 模式，用 TanStack Query 替代 SWR，
 * staleTime=30min 替代 localStorage 缓存。
 *
 * 用法：
 *   const { options, getLabel, getColor } = useDict("sys_status")
 *
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"
import { useMemo } from "react"
import { type DictDataVO, dictApi } from "@/lib/api/rest/admin/dict"

const STALE_TIME = 30 * 60 * 1000 // 30 分钟

/** 全量字典 Map，按 dictType 分组（用于批量初始化） */
export function useDictMap() {
  return useQuery({
    queryKey: ["dict", "all"],
    queryFn: async () => {
      const list = await dictApi.listAll()
      // 后端返回扁平列表，前端按 dictType 分组转为 Map
      return list.reduce<Record<string, DictDataVO[]>>((acc, item) => {
        if (!acc[item.dictType]) acc[item.dictType] = []
        acc[item.dictType].push(item)
        return acc
      }, {})
    },
    staleTime: STALE_TIME
  })
}

/** 按字典类型查询，返回便捷工具函数 */
export function useDict(dictType: string) {
  const { data, isLoading } = useQuery({
    queryKey: ["dict", dictType],
    queryFn: () => dictApi.getByType(dictType),
    staleTime: STALE_TIME,
    enabled: !!dictType
  })

  return useMemo(() => {
    const options: DictDataVO[] = data ?? []

    return {
      isLoading,
      /** 原始选项数组 */
      options,
      /** value 转为数字的选项（数值类型字典用） */
      intOptions: options.map((d) => ({ ...d, value: parseInt(d.value, 10) })),
      /** 获取标签 */
      getLabel: (value: string | number): string =>
        options.find((d) => d.value === String(value))?.label ?? "",
      /** 获取颜色（colorType 字段） */
      getColor: (value: string | number): string =>
        options.find((d) => d.value === String(value))?.colorType ?? "default",
      /** 获取完整字典项 */
      getItem: (value: string | number): DictDataVO | undefined =>
        options.find((d) => d.value === String(value))
    }
  }, [data, isLoading])
}
