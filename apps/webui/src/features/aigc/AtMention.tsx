/**
 * @提及组件——在 Prompt 中输入 @ 触发素材搜索下拉
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useEffect, useState } from "react"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { useMediaAssetSearch } from "@/lib/queries/use-media-assets"
import { useAigcStore } from "./store"

interface AtMentionProps {
  /** 当前 textarea 的值 */
  value: string
  /** 更新 textarea 值 */
  onChange: (value: string) => void
  /** textarea ref，用于获取光标位置 */
  textareaRef: React.RefObject<HTMLTextAreaElement | null>
}

export function AtMention({ value, onChange, textareaRef }: AtMentionProps) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState("")
  const [atPosition, setAtPosition] = useState(-1)
  const addReferenceAsset = useAigcStore((s) => s.addReferenceAsset)
  const { data: results = [] } = useMediaAssetSearch(keyword)

  /** 监听 textarea 输入，检测 @ 触发 */
  const handleInput = useCallback(() => {
    const textarea = textareaRef.current
    if (!textarea) return

    const cursorPos = textarea.selectionStart
    const textBefore = value.slice(0, cursorPos)
    const atIndex = textBefore.lastIndexOf("@")

    if (atIndex >= 0) {
      const charBeforeAt = atIndex > 0 ? textBefore[atIndex - 1] : " "
      // @ 前必须是空格或行首
      if (charBeforeAt === " " || charBeforeAt === "\n" || atIndex === 0) {
        const query = textBefore.slice(atIndex + 1)
        // 搜索词不含空格（空格表示已结束输入）
        if (!query.includes(" ") && !query.includes("\n")) {
          setKeyword(query)
          setAtPosition(atIndex)
          setOpen(true)
          return
        }
      }
    }
    setOpen(false)
  }, [value, textareaRef])

  useEffect(() => {
    handleInput()
  }, [handleInput])

  /** 选择素材后插入引用 */
  function handleSelect(assetName: string) {
    const asset = results.find((a) => a.name === assetName)
    if (!asset) return

    const mention = `@${asset.name} `
    const cursorPos = textareaRef.current?.selectionStart ?? value.length
    const newValue = value.slice(0, atPosition) + mention + value.slice(cursorPos)
    onChange(newValue)
    addReferenceAsset(asset)
    setOpen(false)
    setKeyword("")
    // 恢复焦点
    requestAnimationFrame(() => {
      const textarea = textareaRef.current
      if (textarea) {
        const newCursor = atPosition + mention.length
        textarea.focus()
        textarea.setSelectionRange(newCursor, newCursor)
      }
    })
  }

  if (!open) return null

  return (
    <div className="absolute bottom-full left-0 z-50 mb-1 w-[240px] rounded-lg border border-border bg-popover shadow-xl">
      <Command shouldFilter={false}>
        <CommandInput
          value={keyword}
          onValueChange={setKeyword}
          placeholder="搜索素材..."
          className="h-8 text-xs"
        />
        <CommandList>
          <CommandEmpty className="py-2 text-center text-muted-foreground text-xs">
            未找到素材
          </CommandEmpty>
          <CommandGroup>
            {results.map((asset) => (
              <CommandItem
                key={asset.id}
                value={asset.name}
                onSelect={handleSelect}
                className="gap-2"
              >
                {/* biome-ignore lint/performance/noImgElement: 动态素材缩略图 */}
                <img
                  src={asset.thumbnail}
                  alt={asset.name}
                  className="size-6 rounded-sm object-cover"
                />
                <span className="truncate text-xs">{asset.name}</span>
              </CommandItem>
            ))}
          </CommandGroup>
        </CommandList>
      </Command>
    </div>
  )
}
