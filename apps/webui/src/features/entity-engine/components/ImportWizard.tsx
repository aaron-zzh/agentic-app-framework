/**
 * ImportWizard——数据导入向导（上传→映射→预览→执行→结果）
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"

import type { DataFieldDef, EntityDef } from "@/features/entity-engine/types"

type Step = "upload" | "mapping" | "preview" | "result"

interface ImportWizardProps {
  entity: EntityDef
  open: boolean
  onClose: () => void
}

/** 导入向导 */
export function ImportWizard({ entity, open, onClose }: ImportWizardProps) {
  const [step, setStep] = useState<Step>("upload")
  const [file, setFile] = useState<File | null>(null)
  const [headers, setHeaders] = useState<string[]>([])
  const [mapping, setMapping] = useState<Record<string, string>>({})
  const [result, setResult] = useState<{ success: number; errors: number } | null>(null)

  const entityFields = entity.fields.filter(
    (f): f is DataFieldDef =>
      "name" in f && f.type !== "group" && f.type !== "tabs" && f.type !== "row"
  )

  // 上传文件并解析表头
  const handleUpload = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const f = e.target.files?.[0]
      if (!f) return
      setFile(f)

      // 读取 CSV 第一行作为表头
      const reader = new FileReader()
      reader.onload = (ev) => {
        const text = ev.target?.result as string
        const firstLine = text.split("\n")[0] ?? ""
        const cols = firstLine.split(",").map((c) => c.trim().replace(/^"|"$/g, ""))
        setHeaders(cols)

        // 自动匹配：列名与字段 name/label 相同时自动映射
        const autoMap: Record<string, string> = {}
        for (const col of cols) {
          const match = entityFields.find(
            (f) => f.name === col || f.label === col || f.name.toLowerCase() === col.toLowerCase()
          )
          if (match) autoMap[col] = match.name
        }
        setMapping(autoMap)
        setStep("mapping")
      }
      reader.readAsText(f)
    },
    [entityFields]
  )

  // 执行导入
  const handleImport = useCallback(async () => {
    if (!file) return
    const formData = new FormData()
    formData.append("file", file)
    formData.append("mapping", JSON.stringify(mapping))

    try {
      const res = await fetch(`${entity.apiPath}/import`, {
        method: "POST",
        body: formData
      })
      const json = await res.json()
      setResult({ success: json.data?.success ?? 0, errors: json.data?.errors ?? 0 })
      setStep("result")
    } catch {
      setResult({ success: 0, errors: -1 })
      setStep("result")
    }
  }, [file, mapping, entity])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} onKeyDown={undefined} />
      <div className="relative w-full max-w-lg rounded-lg border bg-background p-6 shadow-xl">
        <h2 className="mb-4 font-semibold text-lg">导入 {entity.label}</h2>

        {step === "upload" && (
          <div className="space-y-4">
            <p className="text-muted-foreground text-sm">上传 CSV 文件</p>
            <input type="file" accept=".csv,.xlsx" onChange={handleUpload} />
          </div>
        )}

        {step === "mapping" && (
          <div className="space-y-3">
            <p className="text-muted-foreground text-sm">字段映射（{headers.length} 列）</p>
            <div className="max-h-64 space-y-2 overflow-auto">
              {headers.map((col) => (
                <div key={col} className="flex items-center gap-2">
                  <span className="w-32 truncate text-sm">{col}</span>
                  <span className="text-muted-foreground">→</span>
                  <select
                    className="h-7 flex-1 rounded border px-2 text-sm"
                    value={mapping[col] ?? ""}
                    onChange={(e) => setMapping({ ...mapping, [col]: e.target.value })}
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
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                className="rounded border px-3 py-1.5 text-sm"
                onClick={() => setStep("upload")}
              >
                上一步
              </button>
              <button
                type="button"
                className="rounded bg-primary px-3 py-1.5 text-primary-foreground text-sm"
                onClick={handleImport}
              >
                开始导入
              </button>
            </div>
          </div>
        )}

        {step === "result" && result && (
          <div className="space-y-3 text-center">
            <p className="font-medium text-lg">{result.errors === -1 ? "导入失败" : "导入完成"}</p>
            {result.errors !== -1 && (
              <p className="text-muted-foreground text-sm">
                成功 {result.success} 条，失败 {result.errors} 条
              </p>
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
