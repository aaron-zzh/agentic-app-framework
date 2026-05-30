/**
 * EntityDef JSON 编辑器——Monaco Editor + 实时预览
 * @author AaronZZH & Kiro
 *
 * 左侧 Monaco Editor 编辑 EntityDef JSON，右侧 ViewEngine 实时预览。
 * 支持 JSON Schema 校验和自动补全。
 */

"use client"

import Editor, { type OnMount } from "@monaco-editor/react"
import { Save } from "lucide-react"
import { useTheme } from "next-themes"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { ViewErrorBoundary } from "@/components/common/ViewErrorBoundary"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable"
import { ViewEngine } from "@/features/entity-engine/components/ViewEngine"
import type { EntityDef } from "@/features/entity-engine/types"

import { entityDefJsonSchema } from "./entity-def-schema"

interface EntityDefEditorProps {
  /** 当前编辑的 JSON 字符串 */
  value: string
  /** 是否为内置实体（只读） */
  builtin?: boolean
  /** 保存回调 */
  onSave: (json: string) => void
  /** 保存中状态 */
  saving?: boolean
}

/** EntityDef JSON 编辑器（左右分屏：编辑器 + 预览） */
export function EntityDefEditor({ value, builtin, onSave, saving }: EntityDefEditorProps) {
  const [editorValue, setEditorValue] = useState(value)
  const [parseError, setParseError] = useState<string | null>(null)
  const editorRef = useRef<Parameters<OnMount>[0] | null>(null)
  const { resolvedTheme } = useTheme()

  // 外部 value 变化时同步
  useEffect(() => {
    setEditorValue(value)
  }, [value])

  /** 尝试解析为 EntityDef 用于预览 */
  const previewEntity = useMemo((): EntityDef | null => {
    try {
      const parsed = JSON.parse(editorValue) as EntityDef
      if (parsed.slug && parsed.fields && parsed.listView) {
        setParseError(null)
        return parsed
      }
      setParseError("缺少必填字段：slug / fields / listView")
      return null
    } catch (e) {
      setParseError(e instanceof Error ? e.message : "JSON 解析失败")
      return null
    }
  }, [editorValue])

  /** Monaco 挂载时注册 JSON Schema */
  const handleEditorMount: OnMount = useCallback(
    (editor, monaco) => {
      editorRef.current = editor

      // 注册 EntityDef JSON Schema
      monaco.languages.json.jsonDefaults.setDiagnosticsOptions({
        validate: true,
        schemas: [
          {
            uri: "https://aaf.xuejiai.com/schemas/entity-def.json",
            fileMatch: ["*"],
            schema: entityDefJsonSchema
          }
        ]
      })

      // 快捷键保存
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
        if (!builtin) {
          onSave(editor.getValue())
        }
      })
    },
    [builtin, onSave]
  )

  const handleSave = useCallback(() => {
    onSave(editorValue)
  }, [editorValue, onSave])

  const isDirty = editorValue !== value

  return (
    <div className="flex h-full flex-col">
      {/* 工具栏 */}
      <div className="flex items-center justify-between border-b px-4 py-2">
        <div className="flex items-center gap-2">
          <span className="font-medium text-sm">配置编辑器</span>
          {builtin && <Badge variant="secondary">内置（只读）</Badge>}
          {isDirty && !builtin && <Badge variant="default">未保存</Badge>}
          {parseError && <Badge variant="destructive">语法错误</Badge>}
        </div>
        {!builtin && (
          <Button size="sm" disabled={saving || !isDirty} onClick={handleSave}>
            <Save className="mr-1 h-4 w-4" />
            {saving ? "保存中..." : "保存"}
          </Button>
        )}
      </div>

      {/* 编辑器 + 预览 */}
      <ResizablePanelGroup direction="horizontal" className="flex-1">
        <ResizablePanel defaultSize={50} minSize={30}>
          <Editor
            language="json"
            theme={resolvedTheme === "dark" ? "vs-dark" : "vs"}
            value={editorValue}
            onChange={(v) => setEditorValue(v ?? "")}
            onMount={handleEditorMount}
            options={{
              readOnly: builtin,
              minimap: { enabled: false },
              fontSize: 13,
              lineNumbers: "on",
              scrollBeyondLastLine: false,
              automaticLayout: true,
              tabSize: 2,
              formatOnPaste: true
            }}
          />
        </ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel defaultSize={50} minSize={20}>
          <div className="h-full overflow-auto bg-muted/30 p-4">
            <p className="mb-2 font-medium text-muted-foreground text-xs">实时预览</p>
            {previewEntity ? (
              <ViewErrorBoundary>
                <ViewEngine entity={previewEntity} view="list" />
              </ViewErrorBoundary>
            ) : (
              <div className="flex items-center justify-center rounded-md border border-dashed p-8 text-muted-foreground text-sm">
                {parseError ?? "等待有效的 EntityDef 配置..."}
              </div>
            )}
          </div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
