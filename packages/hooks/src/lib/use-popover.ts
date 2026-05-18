import { useCallback, useState } from "react"

/**
 * Popover 状态管理 Hook，管理开关状态和锚点元素
 *
 * @example
 * const { open, onOpen, onClose, anchorEl } = usePopover();
 * <button onClick={onOpen}>打开</button>
 * <Popover open={open} anchorEl={anchorEl} onClose={onClose} />
 */
export function usePopover() {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

  const onOpen = useCallback((event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget)
  }, [])

  const onClose = useCallback(() => {
    setAnchorEl(null)
  }, [])

  return { open: Boolean(anchorEl), anchorEl, onOpen, onClose }
}
