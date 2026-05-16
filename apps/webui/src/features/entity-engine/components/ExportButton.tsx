/**
 * ExportButton——列表导出（CSV/XLSX）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"

interface ExportButtonProps {
  entity: EntityDef
}

/** 导出按钮 + 格式选择 */
export function ExportButton({ entity }: ExportButtonProps) {
  const [open, setOpen] = useState(false)

  const handleExport = useCallback(
    (format: "csv" | "xlsx") => {
      const fields = entity.fields
        .filter(
          (f): f is DataFieldDef =>
            "name" in f && f.type !== "group" && f.type !== "tabs" && f.type !== "row"
        )
        .map((f) => f.name)
        .join(",")

      // 触发后端导出下载
      const url = `${entity.apiPath}/export?format=${format}&fields=${fields}`
      window.open(url, "_blank")
      setOpen(false)
    },
    [entity]
  )

  return (
    <div className="relative inline-block">
      <button
        type="button"
        className="h-7 rounded border px-2 text-muted-foreground text-xs hover:text-foreground"
        onClick={() => setOpen(!open)}
      >
        ↓ 导出
      </button>
      {open && (
        <div className="absolute top-8 right-0 z-20 w-32 rounded-md border bg-background shadow-md">
          <button
            type="button"
            className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted"
            onClick={() => handleExport("csv")}
          >
            CSV
          </button>
          <button
            type="button"
            className="w-full px-3 py-1.5 text-left text-sm hover:bg-muted"
            onClick={() => handleExport("xlsx")}
          >
            XLSX
          </button>
        </div>
      )}
    </div>
  )
}
