/**
 * 文档大纲组件——解析 Markdown 标题（H1/H2/H3）生成大纲导航
 * @author AaronZZH & Kiro
 */
"use client"

interface OutlineItem {
  level: number
  text: string
  anchor: string
}

interface DocOutlineProps {
  content: string | undefined
  /** 点击大纲项时的回调（可用于滚动定位） */
  onSelect?: (anchor: string) => void
}

function parseOutline(content: string): OutlineItem[] {
  return content
    .split("\n")
    .filter((line) => /^#{1,3} /.test(line))
    .map((line) => {
      const match = line.match(/^(#{1,3}) (.+)/)
      if (!match) return null
      const level = match[1].length
      const text = match[2].trim()
      const anchor = text.toLowerCase().replace(/[^\w\u4e00-\u9fa5]+/g, "-")
      return { level, text, anchor }
    })
    .filter((item): item is OutlineItem => item !== null)
}

export function DocOutline({ content, onSelect }: DocOutlineProps) {
  if (!content) return null

  const items = parseOutline(content)
  if (items.length === 0) return null

  return (
    <div className="mt-4 border-t pt-3">
      <p className="mb-2 px-2 font-medium text-muted-foreground text-xs">大纲</p>
      <ul className="space-y-0.5">
        {items.map((item, i) => (
          <li key={`${item.anchor}-${i}`}>
            <button
              type="button"
              className="w-full truncate rounded px-2 py-0.5 text-left text-xs hover:bg-accent hover:text-accent-foreground"
              style={{ paddingLeft: `${(item.level - 1) * 10 + 8}px` }}
              onClick={() => onSelect?.(item.anchor)}
              title={item.text}
            >
              {item.text}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
