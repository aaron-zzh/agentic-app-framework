/**
 * RoleSelector——助理角色选择器
 * @author AaronZZH & Kiro
 */

"use client"

import { Check, ChevronDown } from "lucide-react"
import { useState } from "react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList
} from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import { useAssistants } from "@/lib/queries/use-assistants"

interface RoleSelectorProps {
  value: string
  onChange: (roleId: string) => void
}

export function RoleSelector({ value, onChange }: RoleSelectorProps) {
  const [open, setOpen] = useState(false)
  const { data: assistants = [] } = useAssistants()

  const currentAssistant = assistants.find(
    (a) => a.assistantId === value || (a.roles ?? []).some((r) => r.roleId === value)
  )
  const currentRole = currentAssistant?.roles?.find((r) => r.roleId === value)
  const displayName = currentAssistant
    ? currentRole
      ? `${currentAssistant.name} · ${currentRole.name}`
      : currentAssistant.name
    : null

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger className="inline-flex h-8 items-center gap-1.5 rounded-md border bg-background px-2 font-normal text-xs shadow-xs hover:bg-accent">
        {currentAssistant ? (
          <>
            <Avatar className="size-4">
              <AvatarImage src={currentAssistant.avatar} />
              <AvatarFallback className="text-[8px]">
                {currentAssistant.name.charAt(0)}
              </AvatarFallback>
            </Avatar>
            <span className="max-w-[100px] truncate">{displayName}</span>
          </>
        ) : (
          <span className="text-muted-foreground">选择角色</span>
        )}
        <ChevronDown className="size-3 text-muted-foreground" />
      </PopoverTrigger>
      <PopoverContent className="w-60 p-0" align="start">
        <Command>
          <CommandInput placeholder="搜索助理或角色..." className="h-8 text-xs" />
          <CommandList>
            <CommandEmpty className="py-4 text-center text-muted-foreground text-xs">
              未找到助理
            </CommandEmpty>
            <CommandGroup>
              {assistants.map((assistant, idx) => (
                <>
                  <CommandItem
                    key={assistant.assistantId}
                    value={`${assistant.name}`}
                    onSelect={() => {
                      onChange(assistant.assistantId)
                      setOpen(false)
                    }}
                    className={`gap-2 text-xs${idx > 0 ? "mt-1" : ""}`}
                  >
                    <Avatar className="size-4 shrink-0">
                      <AvatarImage src={assistant.avatar} />
                      <AvatarFallback className="text-[8px]">
                        {assistant.name.charAt(0)}
                      </AvatarFallback>
                    </Avatar>
                    <span className="flex-1 truncate font-medium">{assistant.name}</span>
                    {value === assistant.assistantId && <Check className="size-3 shrink-0" />}
                  </CommandItem>
                  {(assistant.roles ?? []).map((role) => (
                    <CommandItem
                      key={role.roleId}
                      value={`${assistant.name} ${role.name}`}
                      onSelect={() => {
                        onChange(role.roleId)
                        setOpen(false)
                      }}
                      className="gap-2 pl-8 text-xs"
                    >
                      <span className="flex-1 truncate text-muted-foreground">{role.name}</span>
                      {value === role.roleId && <Check className="size-3 shrink-0" />}
                    </CommandItem>
                  ))}
                </>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
