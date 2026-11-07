/**
 * 从文档库导入知识库：按标题搜索、多选文档，按当前版本导入
 * @author AaronZZH & Kiro
 */

"use client"

import { Library } from "lucide-react"
import { useId, useMemo, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ProjectDocumentPicker } from "@/features/studio/content/ProjectDocumentPicker"
import { useImportKnowledgeFromDocs } from "@/lib/api/rest/knowledge/knowledge"
import { useDocList } from "@/lib/api/rest/system/document"

/** 知识库上传自动登记的来源文档没有可导入正文，不出现在选择列表中。 */
const KNOWLEDGE_SOURCE_DOC_TYPE = "knowledge_source"

/** 单次导入上限，与后端校验一致。 */
const MAX_IMPORT_DOCUMENTS = 20

interface DocumentLibraryImportProps {
  knowledgeBaseId: string
}

export function DocumentLibraryImport({ knowledgeBaseId }: DocumentLibraryImportProps) {
  const uid = useId()
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState("")
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const { data: documents = [], isLoading } = useDocList()
  const importDocs = useImportKnowledgeFromDocs(knowledgeBaseId)

  const options = useMemo(() => {
    const normalized = keyword.trim().toLowerCase()
    return documents.filter(
      (document) =>
        document.docType !== KNOWLEDGE_SOURCE_DOC_TYPE &&
        (normalized === "" || document.title.toLowerCase().includes(normalized))
    )
  }, [documents, keyword])

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      setKeyword("")
      setSelectedIds([])
    }
  }

  function handleSelect(values: number[]) {
    if (values.length > MAX_IMPORT_DOCUMENTS) {
      toast.error(`单次最多导入 ${MAX_IMPORT_DOCUMENTS} 个文档`)
      return
    }
    setSelectedIds(values)
  }

  function handleImport() {
    importDocs.mutate(selectedIds, {
      onSuccess: ({ imported, skipped }) => {
        if (imported.length > 0) {
          toast.success(`已导入 ${imported.length} 个文档，正在后台处理`)
        }
        if (skipped.length > 0) {
          const titles = new Map(documents.map((document) => [document.id, document.title]))
          const detail = skipped
            .map((item) => `${titles.get(item.documentId) ?? item.documentId}：${item.reason}`)
            .join("；")
          toast.info(`跳过 ${skipped.length} 个文档：${detail}`)
        }
        handleOpenChange(false)
      },
      onError: (error) => toast.error(`导入失败：${error.message}`)
    })
  }

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <Library />
        从文档库导入
      </Button>
      <Dialog open={open} onOpenChange={handleOpenChange}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>从文档库导入</DialogTitle>
            <DialogDescription>
              按文档当前版本导入；同一版本不会重复导入，文档更新后再次导入会替换旧版本。
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <label htmlFor={`${uid}-keyword`} className="sr-only">
              搜索文档
            </label>
            <Input
              id={`${uid}-keyword`}
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="按标题搜索文档"
            />
            <ProjectDocumentPicker
              values={selectedIds}
              options={options}
              loading={isLoading}
              disabled={importDocs.isPending}
              emptyText={keyword.trim() ? "没有匹配的文档" : "文档库暂无可导入的文档"}
              onValueChange={handleSelect}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => handleOpenChange(false)}>
              取消
            </Button>
            <Button
              disabled={selectedIds.length === 0 || importDocs.isPending}
              onClick={handleImport}
            >
              {importDocs.isPending
                ? "导入中…"
                : `导入${selectedIds.length > 0 ? `（${selectedIds.length}）` : ""}`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
