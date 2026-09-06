/**
 * 媒体图片来源菜单：先选择本地上传或项目素材，再进入对应选择流程。
 *
 * @author AaronZZH & Kiro
 */

"use client"

import { ImagePlus, Images, Upload } from "lucide-react"
import { useRef, useState } from "react"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger
} from "@/components/ui/dropdown-menu"
import { ProjectReferenceImagePicker } from "@/features/studio/media-generation/components/ProjectReferenceImagePicker"
import type {
  MediaImageAttachment,
  MediaProjectTarget
} from "@/features/studio/media-generation/types"

interface MediaImageSourceMenuProps {
  label: string
  disabled?: boolean
  multiple?: boolean
  compact?: boolean
  projectTarget?: MediaProjectTarget
  onSelectFiles: (files: File[]) => void
  onSelectProject: (attachment: MediaImageAttachment) => void
}

/** 提供统一的本地上传与项目素材图片入口。 */
export function MediaImageSourceMenu({
  label,
  disabled = false,
  multiple = false,
  compact = false,
  projectTarget,
  onSelectFiles,
  onSelectProject
}: MediaImageSourceMenuProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [pickerOpen, setPickerOpen] = useState(false)

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger
          disabled={disabled}
          render={
            <Button
              type="button"
              variant={compact ? "ghost" : "outline"}
              size={compact ? "icon-sm" : "sm"}
              className={compact ? "shrink-0" : "h-14 shrink-0 flex-col border-dashed text-xs"}
              aria-label={label}
            />
          }
        >
          <ImagePlus />
          {compact ? null : label}
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuGroup>
            <DropdownMenuItem onClick={() => inputRef.current?.click()}>
              <Upload />
              本地上传
            </DropdownMenuItem>
            {projectTarget ? (
              <DropdownMenuItem onClick={() => setPickerOpen(true)}>
                <Images />
                项目素材
              </DropdownMenuItem>
            ) : null}
          </DropdownMenuGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple={multiple}
        className="hidden"
        disabled={disabled}
        onChange={(event) => {
          const files = Array.from(event.target.files ?? [])
          if (files.length > 0) onSelectFiles(files)
          event.target.value = ""
        }}
      />
      {projectTarget ? (
        <ProjectReferenceImagePicker
          open={pickerOpen}
          projectId={projectTarget.projectId}
          onOpenChange={setPickerOpen}
          onSelect={onSelectProject}
        />
      ) : null}
    </>
  )
}
