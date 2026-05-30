/**
 * PivotView——透视视图（维度/指标拖拽配置 + 后端 GROUP BY 聚合）
 * @author AaronZZH & Kiro
 *
 * 用法：
 * ```tsx
 * <PivotView entity={entityDef} />
 * ```
 */

"use client"

import { useCallback, useState } from "react"

import type {
  DataFieldDef,
  EntityDef,
  PivotConfig,
  PivotMeasure
} from "@/features/entity-engine/types"
import { useEntitySearchParams } from "@/lib/queries/use-entity-search-params"

interface PivotViewProps {
  entity: EntityDef
}

type PivotResult = Record<string, unknown>[]

/** 透视视图 */
export function PivotView({ entity }: PivotViewProps) {
  const { pivotView } = entity
  if (!pivotView?.enabled) {
    return (
      <div className="flex items-center justify-center p-8 text-muted-foreground text-sm">
        该实体未启用透视视图
      </div>
    )
  }

  return <PivotViewInner entity={entity} />
}

function PivotViewInner({ entity }: PivotViewProps) {
  const { pivotView } = entity
  const [params] = useEntitySearchParams()

  const [config, setConfig] = useState<PivotConfig>(
    pivotView?.defaultConfig ?? { rows: [], values: [] }
  )
  const [result, setResult] = useState<PivotResult | null>(null)
  const [loading, setLoading] = useState(false)

  const dataFields = entity.fields.filter((f): f is DataFieldDef => "name" in f)

  const dimensionFields = dataFields.filter((f) => pivotView?.dimensions.includes(f.name))

  const measures = pivotView?.measures ?? []

  // 执行透视查询
  const runPivot = useCallback(async () => {
    if (config.rows.length === 0 || config.values.length === 0) return
    setLoading(true)
    try {
      const res = await fetch(`${entity.apiPath}/pivot`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...config, search: params.search })
      })
      const json = await res.json()
      setResult(json.data ?? [])
    } catch {
      setResult([])
    } finally {
      setLoading(false)
    }
  }, [config, entity, params.search])

  const addRow = (field: string) => {
    if (!config.rows.includes(field)) {
      setConfig((c) => ({ ...c, rows: [...c.rows, field] }))
    }
  }

  const removeRow = (field: string) => {
    setConfig((c) => ({ ...c, rows: c.rows.filter((r) => r !== field) }))
  }

  const addValue = (measure: PivotMeasure) => {
    const agg = measure.aggregations[0] ?? "count"
    const exists = config.values.some((v) => v.field === measure.field && v.aggregation === agg)
    if (!exists) {
      setConfig((c) => ({
        ...c,
        values: [...c.values, { field: measure.field, aggregation: agg }]
      }))
    }
  }

  const removeValue = (field: string, aggregation: string) => {
    setConfig((c) => ({
      ...c,
      values: c.values.filter((v) => !(v.field === field && v.aggregation === aggregation))
    }))
  }

  const getFieldLabel = (name: string) => dataFields.find((f) => f.name === name)?.label ?? name

  // 从结果中提取列头
  const resultColumns = result && result.length > 0 ? Object.keys(result[0]) : []

  return (
    <div className="flex h-full gap-0">
      {/* 左侧维度面板 */}
      <div className="w-52 shrink-0 border-r p-3">
        <p className="mb-2 font-medium text-muted-foreground text-xs uppercase tracking-wide">
          维度
        </p>
        <div className="mb-3 space-y-1">
          {dimensionFields.map((f) => (
            <button
              key={f.name}
              type="button"
              className="flex w-full items-center justify-between rounded px-2 py-1 text-left text-sm hover:bg-accent"
              onClick={() => addRow(f.name)}
            >
              <span>{f.label ?? f.name}</span>
              <span className="text-muted-foreground text-xs">+行</span>
            </button>
          ))}
        </div>

        <p className="mb-2 font-medium text-muted-foreground text-xs uppercase tracking-wide">
          指标
        </p>
        <div className="space-y-1">
          {measures.map((m) => (
            <button
              key={m.field}
              type="button"
              className="flex w-full items-center justify-between rounded px-2 py-1 text-left text-sm hover:bg-accent"
              onClick={() => addValue(m)}
            >
              <span>{m.label ?? getFieldLabel(m.field)}</span>
              <span className="text-muted-foreground text-xs">{m.aggregations[0]}</span>
            </button>
          ))}
        </div>
      </div>

      {/* 右侧配置 + 结果 */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* 配置区 */}
        <div className="flex items-start gap-4 border-b p-3">
          {/* 行维度 */}
          <div className="min-w-0 flex-1">
            <p className="mb-1 text-muted-foreground text-xs">行</p>
            <div className="flex flex-wrap gap-1">
              {config.rows.map((r) => (
                <span
                  key={r}
                  className="flex items-center gap-1 rounded-full bg-accent px-2 py-0.5 text-xs"
                >
                  {getFieldLabel(r)}
                  <button
                    type="button"
                    onClick={() => removeRow(r)}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    ×
                  </button>
                </span>
              ))}
              {config.rows.length === 0 && (
                <span className="text-muted-foreground text-xs">点击左侧维度添加</span>
              )}
            </div>
          </div>

          {/* 值 */}
          <div className="min-w-0 flex-1">
            <p className="mb-1 text-muted-foreground text-xs">值</p>
            <div className="flex flex-wrap gap-1">
              {config.values.map((v) => (
                <span
                  key={`${v.field}-${v.aggregation}`}
                  className="flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-xs"
                >
                  {v.aggregation}({getFieldLabel(v.field)})
                  <button
                    type="button"
                    onClick={() => removeValue(v.field, v.aggregation)}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    ×
                  </button>
                </span>
              ))}
              {config.values.length === 0 && (
                <span className="text-muted-foreground text-xs">点击左侧指标添加</span>
              )}
            </div>
          </div>

          <button
            type="button"
            className="shrink-0 rounded bg-primary px-3 py-1.5 text-primary-foreground text-sm disabled:opacity-50"
            disabled={config.rows.length === 0 || config.values.length === 0 || loading}
            onClick={runPivot}
          >
            {loading ? "查询中…" : "查询"}
          </button>
        </div>

        {/* 结果区 */}
        <div className="flex-1 overflow-auto p-3">
          {result === null && (
            <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
              配置维度和指标后点击查询
            </div>
          )}
          {result !== null && result.length === 0 && (
            <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
              暂无数据
            </div>
          )}
          {result !== null && result.length > 0 && (
            <table className="w-full caption-bottom text-sm">
              <thead className="border-b">
                <tr>
                  {resultColumns.map((col) => (
                    <th
                      key={col}
                      className="h-9 px-3 text-left align-middle font-medium text-muted-foreground"
                    >
                      {col}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {result.map((row, i) => (
                  <tr key={i} className="border-b hover:bg-muted/50">
                    {resultColumns.map((col) => (
                      <td key={col} className="h-9 px-3 align-middle">
                        {String(row[col] ?? "—")}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}
