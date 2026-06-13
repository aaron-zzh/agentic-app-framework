/**
 * 文案普通模式编辑器——输入主题/关键词或粘贴待改写文案，流式生成结果
 * 与 ViralWizardEditor 并列，由面板按类型二选一渲染
 * @author AaronZZH & Kiro
 */

import { Label } from "@/components/ui/label"
import { StreamingEditor, type StreamingEditorHandle } from "@/features/rich-text-editor"
import { PromptTemplateDialog } from "../generation/PromptTemplateDialog"

interface CopywritingEditorProps {
  value: string
  onChange: (v: string) => void
  editorRef: React.RefObject<StreamingEditorHandle | null>
}

export function CopywritingEditor({ value, onChange, editorRef }: CopywritingEditorProps) {
  return (
    <div className="flex min-h-[120px] flex-1 flex-col gap-1">
      <div className="flex items-center justify-between">
        <Label className="text-muted-foreground text-xs">文案内容</Label>
        <PromptTemplateDialog type="COPYWRITING" onSelect={onChange} />
      </div>
      <div className="relative flex-1 overflow-hidden rounded-md border">
        <StreamingEditor
          ref={editorRef}
          value={value}
          onChange={onChange}
          placeholder="在此输入主题或关键词，或直接粘贴需要改写的文案..."
          className="relative h-full"
        />
      </div>
    </div>
  )
}
