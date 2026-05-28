/**
 * 变量选择器——在属性面板中引用流程变量
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import type { VariableDef } from "../types"

interface VariableSelectorProps {
  variables: VariableDef[]
  onSelect: (varName: string) => void
}

export function VariableSelector({ variables, onSelect }: VariableSelectorProps) {
  const [open, setOpen] = useState(false)

  if (variables.length === 0) return null

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="text-muted-foreground hover:text-foreground text-xs underline"
      >
        插入变量
      </button>
      {open && (
        <div className="bg-popover border-border absolute top-6 z-10 w-48 rounded-md border p-1 shadow-md">
          {variables.map((v) => (
            <button
              key={v.name}
              type="button"
              className="hover:bg-accent flex w-full items-center gap-2 rounded px-2 py-1 text-left text-sm"
              onClick={() => {
                onSelect(`\${${v.name}}`)
                setOpen(false)
              }}
            >
              <span className="text-muted-foreground font-mono text-xs">{v.type}</span>
              <span>{v.name}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
