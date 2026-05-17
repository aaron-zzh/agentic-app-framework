/**
 * ExportButton——列表导出（CSV/XLSX），大数据量时通过 SSE 推送进度
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import { BatchProgressBar } from "@/components/common/BatchProgressBar"
import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"
import { useExportProgress } from "@/lib/hooks/use-export-progress"

interface ExportButtonProps {
  entity: EntityDef
}

/** 导出按钮 + 格式选择 + SSE 进度 */
export function ExportButton({ entity }: ExportButtonProps) {
  const [open, setOpen] = useState(false)
  const { startExport, progress, cancel, reset } = useExportProgress(entity)

  const handleExport = useCallback(
    (format: "csv" | "xlsx") => {
      const fields = entity.fields.filter((f): f is DataFieldDef => "name" in f).map((f) => f.name)

      startExport({ format, fields })
      setOpen(false)
    },
    [entity, startExport]
  )

  const isExporting = progress.status === "running" || progress.status === "pending"

  return (
    <>
      <div className="relative inline-block">
        <button
          type="button"
          className="h-7 rounded border px-2 text-muted-foreground text-xs hover:text-foreground disabled:opacity-50"
          onClick={() => setOpen(!open)}
          disabled={isExporting}
        >
          {isExporting ? `↓ ${progress.percentage}%` : "↓ 导出"}
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

      {/* SSE 进度条（运行中或完成时显示） */}
      {progress.status !== "idle" && (
        <BatchProgressBar
          progress={{
            status: progress.status === "pending" ? "running" : progress.status,
            current: progress.current,
            total: progress.total,
            percentage: progress.percentage,
            taskId: progress.taskId,
            errorMessage: progress.errorMessage
          }}
          onCancel={isExporting ? cancel : undefined}
          onClose={reset}
        />
      )}
    </>
  )
}
