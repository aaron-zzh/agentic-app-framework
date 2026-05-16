/**
 * useTable——列表状态统一管理（选中/排序/分页/紧凑模式）
 * @author AaronZZH & Kiro
 * 参考 next-ts useTable 设计
 */

"use client"

import { useCallback, useState } from "react"

export interface UseTableReturn {
  page: number
  pageSize: number
  order: "asc" | "desc"
  orderBy: string
  dense: boolean
  selected: string[]
  onSort: (field: string) => void
  onSelectRow: (id: string) => void
  onSelectAllRows: (checked: boolean, ids: string[]) => void
  onChangePage: (page: number) => void
  onChangePageSize: (size: number) => void
  onChangeDense: () => void
  onResetPage: () => void
  setSelected: (ids: string[]) => void
}

export interface UseTableProps {
  defaultOrderBy?: string
  defaultOrder?: "asc" | "desc"
  defaultPageSize?: number
}

export function useTable(props?: UseTableProps): UseTableReturn {
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(props?.defaultPageSize ?? 20)
  const [orderBy, setOrderBy] = useState(props?.defaultOrderBy ?? "")
  const [order, setOrder] = useState<"asc" | "desc">(props?.defaultOrder ?? "asc")
  const [dense, setDense] = useState(false)
  const [selected, setSelected] = useState<string[]>([])

  const onSort = useCallback(
    (field: string) => {
      setOrder((prev) => (orderBy === field && prev === "asc" ? "desc" : "asc"))
      setOrderBy(field)
    },
    [orderBy]
  )

  const onSelectRow = useCallback((id: string) => {
    setSelected((prev) => (prev.includes(id) ? prev.filter((v) => v !== id) : [...prev, id]))
  }, [])

  const onSelectAllRows = useCallback((checked: boolean, ids: string[]) => {
    setSelected(checked ? ids : [])
  }, [])

  const onChangePage = useCallback((p: number) => {
    setPage(p)
  }, [])
  const onChangePageSize = useCallback((s: number) => {
    setPage(1)
    setPageSize(s)
  }, [])
  const onChangeDense = useCallback(() => setDense((v) => !v), [])
  const onResetPage = useCallback(() => setPage(1), [])

  return {
    page,
    pageSize,
    order,
    orderBy,
    dense,
    selected,
    onSort,
    onSelectRow,
    onSelectAllRows,
    onChangePage,
    onChangePageSize,
    onChangeDense,
    onResetPage,
    setSelected
  }
}
