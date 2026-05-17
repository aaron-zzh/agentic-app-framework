/**
 * useFieldCascader——级联选择数据逻辑
 * @author AaronZZH & Kiro
 */

import { useCallback, useEffect, useState } from "react"

export interface CascaderLevel {
  relationTo: string
  label: string
  dependsOn?: string
  apiPath: string
}

export interface CascaderOption {
  id: string
  label: string
}

export function useFieldCascader(levels: CascaderLevel[], values: (string | undefined)[]) {
  const [options, setOptions] = useState<CascaderOption[][]>(levels.map(() => []))
  const [loading, setLoading] = useState<boolean[]>(levels.map(() => false))

  const loadLevel = useCallback(
    async (levelIdx: number, parentId?: string) => {
      const level = levels[levelIdx]
      if (!level) return

      setLoading((prev) => prev.map((v, i) => (i === levelIdx ? true : v)))
      try {
        const url = parentId
          ? `${level.apiPath}?${level.dependsOn ?? "parentId"}=${parentId}&limit=100`
          : `${level.apiPath}?limit=100`
        const res = await fetch(url)
        const json = await res.json()
        const list = (json.data?.list ?? json.data ?? []) as Record<string, unknown>[]
        setOptions((prev) =>
          prev.map((v, i) =>
            i === levelIdx
              ? list.map((r) => ({
                  id: r.id as string,
                  label: (r.name ?? r.label ?? r.id) as string
                }))
              : v
          )
        )
      } catch {
        setOptions((prev) => prev.map((v, i) => (i === levelIdx ? [] : v)))
      } finally {
        setLoading((prev) => prev.map((v, i) => (i === levelIdx ? false : v)))
      }
    },
    [levels]
  )

  // 首级自动加载
  useEffect(() => {
    loadLevel(0)
  }, [loadLevel])

  // 上级变更时加载下级
  // biome-ignore lint/correctness/useExhaustiveDependencies: values.join 用于检测数组内容变化，避免数组引用变化导致无限循环
  useEffect(() => {
    for (let i = 1; i < levels.length; i++) {
      const parentVal = values[i - 1]
      if (parentVal) loadLevel(i, parentVal)
      else setOptions((prev) => prev.map((v, idx) => (idx >= i ? [] : v)))
    }
  }, [values.join(","), loadLevel])

  return { options, loading }
}
