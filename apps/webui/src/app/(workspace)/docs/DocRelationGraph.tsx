"use client"

interface DocRelationGraphProps {
  docId: number | null
  onSelectDoc: (id: number) => void
}

export function DocRelationGraph({ docId, onSelectDoc: _onSelectDoc }: DocRelationGraphProps) {
  if (!docId)
    return (
      <div className="flex h-full items-center justify-center text-muted-foreground text-sm">
        请选择文档查看关系图
      </div>
    )
  return <div className="p-4 text-muted-foreground text-sm">关系图谱（文档 ID: {docId}）</div>
}
