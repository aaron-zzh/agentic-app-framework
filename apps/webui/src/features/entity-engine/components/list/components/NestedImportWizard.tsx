/**
 * NestedImportWizard——嵌套导入向导（主从关联，支持 XLSX 多 Sheet / JSON 嵌套）
 * @author AaronZZH & Kiro
 *
 * 流程：上传 → 识别结构 → 关系映射 → 字段映射 → 预览校验 → 执行 → 结果
 */

"use client"

import { useCallback, useId, useState } from "react"

import type { DataFieldDef, EntityDef, NestedImportConfig } from "@/features/entity-engine/types"

type Step = "upload" | "relation" | "mapping" | "preview" | "result"

interface SheetData {
  name: string
  headers: string[]
  rows: Record<string, string>[]
}

interface NestedImportWizardProps {
  entity: EntityDef
  nestedConfig: NestedImportConfig
  open: boolean
  onClose: () => void
}

/** 嵌套导入向导 */
export function NestedImportWizard({
  entity,
  nestedConfig,
  open,
  onClose
}: NestedImportWizardProps) {
  const [step, setStep] = useState<Step>("upload")
  const [sheets, setSheets] = useState<SheetData[]>([])
  // 主表 Sheet 索引
  const uid = useId()
  const [mainSheetIdx, setMainSheetIdx] = useState(0)
  // 子表 Sheet 索引
  const [childSheetIdx, setChildSheetIdx] = useState(1)
  // 主表字段映射：源列 → 目标字段
  const [mainMapping, setMainMapping] = useState<Record<string, string>>({})
  // 子表字段映射
  const [childMapping, setChildMapping] = useState<Record<string, string>>({})
  // 关联列（子表中对应主表 matchBy 的列名）
  const [relationCol, setRelationCol] = useState("")
  const [preview, setPreview] = useState<{ valid: number; orphan: number; errors: string[] }>({
    valid: 0,
    orphan: 0,
    errors: []
  })
  const [result, setResult] = useState<{
    created: number
    childCreated: number
    errors: number
  } | null>(null)

  const entityFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  // 解析 CSV（简单实现，XLSX 需后端解析）
  const parseCSV = (text: string): SheetData => {
    const lines = text.split("\n").filter((l) => l.trim())
    const headers = (lines[0] ?? "").split(",").map((c) => c.trim().replace(/^"|"$/g, ""))
    const rows = lines.slice(1).map((line) => {
      const vals = line.split(",").map((c) => c.trim().replace(/^"|"$/g, ""))
      return Object.fromEntries(headers.map((h, i) => [h, vals[i] ?? ""]))
    })
    return { name: "Sheet1", headers, rows }
  }

  // 自动映射列名到字段
  const autoMap = useCallback((headers: string[], fields: DataFieldDef[]) => {
    const map: Record<string, string> = {}
    for (const col of headers) {
      const match = fields.find(
        (f) => f.name === col || f.label === col || f.name.toLowerCase() === col.toLowerCase()
      )
      if (match) map[col] = match.name
    }
    return map
  }, [])

  // biome-ignore lint/correctness/useExhaustiveDependencies: 依赖项在组件生命周期内稳定
  const handleUpload = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0]
      if (!file) return

      if (file.name.endsWith(".json")) {
        // JSON 嵌套格式：[{ ...mainFields, items: [...] }]
        const reader = new FileReader()
        reader.onload = (ev) => {
          try {
            const data = JSON.parse(ev.target?.result as string)
            const arr = Array.isArray(data) ? data : [data]
            const mainHeaders = Object.keys(arr[0] ?? {}).filter((k) => !Array.isArray(arr[0][k]))
            const childKey = Object.keys(arr[0] ?? {}).find((k) => Array.isArray(arr[0][k])) ?? ""
            const childHeaders =
              childKey && arr[0][childKey]?.length > 0 ? Object.keys(arr[0][childKey][0]) : []

            const mainRows = arr.map((item: Record<string, unknown>) => {
              const row: Record<string, string> = {}
              for (const h of mainHeaders) row[h] = String(item[h] ?? "")
              return row
            })
            const childRows = arr.flatMap((item: Record<string, unknown>) =>
              ((item[childKey] as Record<string, unknown>[]) ?? []).map((c) => {
                const row: Record<string, string> = {}
                for (const h of childHeaders) row[h] = String(c[h] ?? "")
                return row
              })
            )

            const parsedSheets: SheetData[] = [
              { name: entity.label, headers: mainHeaders, rows: mainRows },
              {
                name: nestedConfig.childLabel ?? nestedConfig.childEntity,
                headers: childHeaders,
                rows: childRows
              }
            ]
            setSheets(parsedSheets)
            setMainMapping(autoMap(mainHeaders, entityFields))
            setStep("relation")
          } catch {
            alert("JSON 格式解析失败")
          }
        }
        reader.readAsText(file)
      } else {
        // CSV：单 Sheet，作为主表
        const reader = new FileReader()
        reader.onload = (ev) => {
          const sheet = parseCSV(ev.target?.result as string)
          setSheets([sheet])
          setMainMapping(autoMap(sheet.headers, entityFields))
          setStep("mapping")
        }
        reader.readAsText(file)
      }
    },
    [entity, nestedConfig, entityFields, autoMap]
  )

  // 预览校验：孤儿检测
  const handlePreview = useCallback(() => {
    const mainSheet = sheets[mainSheetIdx]
    const childSheet = sheets[childSheetIdx]
    if (!mainSheet || !childSheet) return

    const mainKeys = new Set(mainSheet.rows.map((r) => r[nestedConfig.matchBy] ?? ""))
    const orphans = childSheet.rows.filter((r) => !mainKeys.has(r[relationCol] ?? ""))
    const errors: string[] = orphans
      .slice(0, 5)
      .map((r, i) => `子表第 ${i + 1} 行：找不到主表记录 "${r[relationCol]}"`)

    setPreview({
      valid: childSheet.rows.length - orphans.length,
      orphan: orphans.length,
      errors
    })
    setStep("preview")
  }, [sheets, mainSheetIdx, childSheetIdx, nestedConfig, relationCol])

  // 执行导入
  const handleImport = useCallback(async () => {
    const mainSheet = sheets[mainSheetIdx]
    const childSheet = sheets[childSheetIdx]
    if (!mainSheet) return

    try {
      const res = await fetch(`${entity.apiPath}/import/nested`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          mainRows: mainSheet.rows,
          childRows: childSheet?.rows ?? [],
          mainMapping,
          childMapping,
          relationCol,
          nestedConfig
        })
      })
      const json = await res.json()
      setResult({
        created: json.data?.created ?? 0,
        childCreated: json.data?.childCreated ?? 0,
        errors: json.data?.errors ?? 0
      })
      setStep("result")
    } catch {
      setResult({ created: 0, childCreated: 0, errors: -1 })
      setStep("result")
    }
  }, [
    sheets,
    mainSheetIdx,
    childSheetIdx,
    mainMapping,
    childMapping,
    relationCol,
    nestedConfig,
    entity
  ])

  if (!open) return null

  const mainSheet = sheets[mainSheetIdx]
  const childSheet = sheets[childSheetIdx]

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <button
        type="button"
        className="absolute inset-0 bg-black/50"
        aria-label="关闭"
        onClick={onClose}
        onKeyDown={(e) => e.key === "Escape" && onClose()}
      />
      <div className="relative w-full max-w-2xl rounded-lg border bg-background p-6 shadow-xl">
        <h2 className="mb-1 font-semibold text-lg">嵌套导入 — {entity.label}</h2>
        <p className="mb-4 text-muted-foreground text-xs">
          支持 JSON 嵌套格式（主从一体）或 CSV（仅主表）
        </p>

        {/* 步骤指示 */}
        <div className="mb-4 flex gap-2 text-xs">
          {(["upload", "relation", "mapping", "preview", "result"] as Step[]).map((s, i) => (
            <span
              key={s}
              className={step === s ? "font-semibold text-primary" : "text-muted-foreground"}
            >
              {i + 1}.{" "}
              {
                {
                  upload: "上传",
                  relation: "关系",
                  mapping: "映射",
                  preview: "预览",
                  result: "结果"
                }[s]
              }
            </span>
          ))}
        </div>

        {/* Step 1: 上传 */}
        {step === "upload" && (
          <div className="space-y-3">
            <p className="text-muted-foreground text-sm">上传 CSV 或 JSON 文件</p>
            <input type="file" accept=".csv,.json" onChange={handleUpload} />
          </div>
        )}

        {/* Step 2: 关系映射（多 Sheet 时） */}
        {step === "relation" && sheets.length >= 2 && (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label htmlFor={`${uid}-main-sheet`} className="mb-1 block font-medium text-sm">
                  主表 Sheet
                </label>
                <select
                  id={`${uid}-main-sheet`}
                  className="h-8 w-full rounded border px-2 text-sm"
                  value={mainSheetIdx}
                  onChange={(e) => setMainSheetIdx(Number(e.target.value))}
                >
                  {sheets.map((s, i) => (
                    <option key={s.name} value={i}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label htmlFor={`${uid}-child-sheet`} className="mb-1 block font-medium text-sm">
                  子表 Sheet
                </label>
                <select
                  id={`${uid}-child-sheet`}
                  className="h-8 w-full rounded border px-2 text-sm"
                  value={childSheetIdx}
                  onChange={(e) => setChildSheetIdx(Number(e.target.value))}
                >
                  {sheets.map((s, i) => (
                    <option key={s.name} value={i}>
                      {s.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div>
              <label htmlFor={`${uid}-relation-col`} className="mb-1 block font-medium text-sm">
                关联列（子表中对应主表 {nestedConfig.matchBy} 的列）
              </label>
              <select
                id={`${uid}-relation-col`}
                className="h-8 w-full rounded border px-2 text-sm"
                value={relationCol}
                onChange={(e) => setRelationCol(e.target.value)}
              >
                <option value="">请选择</option>
                {(childSheet?.headers ?? []).map((h) => (
                  <option key={h} value={h}>
                    {h}
                  </option>
                ))}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setStep("upload")}
              >
                上一步
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-primary-foreground text-sm disabled:opacity-50"
                disabled={!relationCol}
                onClick={() => setStep("mapping")}
              >
                下一步
              </button>
            </div>
          </div>
        )}

        {/* Step 3: 字段映射 */}
        {step === "mapping" && mainSheet && (
          <div className="space-y-3">
            <p className="font-medium text-sm">主表字段映射（{mainSheet.headers.length} 列）</p>
            <div className="max-h-36 space-y-1.5 overflow-auto">
              {mainSheet.headers.map((col) => (
                <div key={col} className="flex items-center gap-2">
                  <span className="w-28 truncate text-sm">{col}</span>
                  <span className="text-muted-foreground text-xs">→</span>
                  <select
                    className="h-7 flex-1 rounded border px-2 text-sm"
                    value={mainMapping[col] ?? ""}
                    onChange={(e) => setMainMapping({ ...mainMapping, [col]: e.target.value })}
                  >
                    <option value="">跳过</option>
                    {entityFields.map((f) => (
                      <option key={f.name} value={f.name}>
                        {f.label ?? f.name}
                      </option>
                    ))}
                  </select>
                </div>
              ))}
            </div>

            {childSheet && childSheet.headers.length > 0 && (
              <>
                <p className="font-medium text-sm">
                  子表字段映射（{childSheet.headers.length} 列）
                </p>
                <div className="max-h-36 space-y-1.5 overflow-auto">
                  {childSheet.headers
                    .filter((h) => h !== relationCol)
                    .map((col) => (
                      <div key={col} className="flex items-center gap-2">
                        <span className="w-28 truncate text-sm">{col}</span>
                        <span className="text-muted-foreground text-xs">→</span>
                        <select
                          className="h-7 flex-1 rounded border px-2 text-sm"
                          value={childMapping[col] ?? ""}
                          onChange={(e) =>
                            setChildMapping({ ...childMapping, [col]: e.target.value })
                          }
                        >
                          <option value="">跳过</option>
                          {entityFields.map((f) => (
                            <option key={f.name} value={f.name}>
                              {f.label ?? f.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    ))}
                </div>
              </>
            )}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setStep(sheets.length >= 2 ? "relation" : "upload")}
              >
                上一步
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-primary-foreground text-sm"
                onClick={handlePreview}
              >
                预览校验
              </button>
            </div>
          </div>
        )}

        {/* Step 4: 预览校验 */}
        {step === "preview" && (
          <div className="space-y-3">
            <div className="rounded-md border p-3 text-sm">
              <p>✓ 有效记录：{preview.valid} 条</p>
              {preview.orphan > 0 && (
                <p className="text-destructive">
                  ⚠ 孤儿子记录：{preview.orphan} 条（找不到主记录）
                </p>
              )}
              {preview.errors.map((e, i) => (
                <p key={i} className="text-destructive text-xs">
                  {e}
                </p>
              ))}
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setStep("mapping")}
              >
                上一步
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-primary-foreground text-sm"
                onClick={handleImport}
              >
                执行导入
              </button>
            </div>
          </div>
        )}

        {/* Step 5: 结果 */}
        {step === "result" && result && (
          <div className="space-y-3 text-center">
            <p className="font-medium text-lg">{result.errors === -1 ? "导入失败" : "导入完成"}</p>
            {result.errors !== -1 && (
              <div className="text-muted-foreground text-sm">
                <p>主记录新建 {result.created} 条</p>
                <p>子记录新建 {result.childCreated} 条</p>
                {result.errors > 0 && <p className="text-destructive">失败 {result.errors} 条</p>}
              </div>
            )}
            <button
              type="button"
              className="rounded bg-primary px-4 py-2 text-primary-foreground text-sm"
              onClick={onClose}
            >
              关闭
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
