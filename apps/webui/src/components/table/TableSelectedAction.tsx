/**
 * TableSelectedAction——选中行后的批量操作浮层
 * @author AaronZZH & Kiro
 */

interface TableSelectedActionProps {
  numSelected: number
  rowCount: number
  onSelectAllRows: (checked: boolean) => void
  action?: React.ReactNode
}

/** 选中浮层 */
export function TableSelectedAction({
  numSelected,
  rowCount,
  onSelectAllRows,
  action,
}: TableSelectedActionProps) {
  if (!numSelected) return null

  return (
    <div className="absolute inset-x-0 top-0 z-10 flex h-10 items-center gap-3 bg-primary/10 px-4">
      <input
        type="checkbox"
        className="h-4 w-4 rounded border"
        checked={numSelected === rowCount}
        ref={(el) => { if (el) el.indeterminate = numSelected > 0 && numSelected < rowCount }}
        onChange={(e) => onSelectAllRows(e.target.checked)}
      />
      <span className="text-sm font-medium text-primary">
        已选 {numSelected} 项
      </span>
      <div className="ml-auto flex items-center gap-1">
        {action}
      </div>
    </div>
  )
}
