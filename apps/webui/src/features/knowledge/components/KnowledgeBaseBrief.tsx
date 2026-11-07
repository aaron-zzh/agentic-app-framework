/**
 * 知识库卡片/选项中的简要信息：知识库说明两行截断，其下以更弱样式单行截断展示涵盖主题
 * @author AaronZZH & Kiro
 *
 * @example
 * <KnowledgeBaseBrief description={kb.description} aiSummary={kb.aiSummary} />
 */

interface KnowledgeBaseBriefProps {
  /** 知识库说明（用户填写） */
  description: string | null
  /** 涵盖主题（系统自动生成） */
  aiSummary: string | null
}

/** 两者均为空时不渲染；仅有涵盖主题时只显示涵盖主题。 */
export function KnowledgeBaseBrief({ description, aiSummary }: KnowledgeBaseBriefProps) {
  if (!description && !aiSummary) return null
  return (
    <div className="mt-0.5 space-y-0.5">
      {description ? (
        <p className="line-clamp-2 text-muted-foreground text-xs">{description}</p>
      ) : null}
      {aiSummary ? (
        <p className="truncate text-muted-foreground/70 text-xs" title={aiSummary}>
          {aiSummary}
        </p>
      ) : null}
    </div>
  )
}
