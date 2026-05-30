/**
 * 菜单管理页面——树形表格展示 + CRUD
 * @author AaronZZH & Kiro
 */

"use client"

import { ChevronDown, ChevronRight, Pencil, Plus, Trash2 } from "lucide-react"
import { useCallback, useId, useState } from "react"
import { toast } from "sonner"

import { PageContainer } from "@/components/common/PageContainer"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from "@/components/ui/alert-dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { TypographyH1 } from "@/components/ui/typography"
import type { MenuCreateDTO, MenuVO } from "@/lib/api/menu"
import { useAllMenus, useCreateMenu, useDeleteMenu, useUpdateMenu } from "@/lib/queries/use-menus"
import { cn } from "@/lib/utils/cn"

/** 菜单类型标签颜色 */
const MENU_TYPE_BADGE: Record<string, string> = {
  GROUP: "bg-blue-100 text-blue-800",
  MENU: "bg-green-100 text-green-800",
  BUTTON: "bg-orange-100 text-orange-800"
}

export default function MenusPage() {
  const { data: menus, isLoading } = useAllMenus()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingMenu, setEditingMenu] = useState<MenuVO | null>(null)

  const handleCreate = useCallback(() => {
    setEditingMenu(null)
    setDialogOpen(true)
  }, [])

  const handleEdit = useCallback((menu: MenuVO) => {
    setEditingMenu(menu)
    setDialogOpen(true)
  }, [])

  return (
    <PageContainer>
      <div className="mb-4 flex items-center justify-between">
        <TypographyH1>菜单管理</TypographyH1>
        <Button onClick={handleCreate}>
          <Plus className="mr-1 size-4" />
          新建菜单
        </Button>
      </div>

      {isLoading ? (
        <div className="space-y-2">
          {["s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"].map((key) => (
            <Skeleton key={key} className="h-10 w-full" />
          ))}
        </div>
      ) : (
        <div className="rounded-md border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-[280px]">标题</TableHead>
                <TableHead>路径</TableHead>
                <TableHead className="w-[80px]">图标</TableHead>
                <TableHead className="w-[80px]">类型</TableHead>
                <TableHead className="w-[60px]">排序</TableHead>
                <TableHead className="w-[60px]">可见</TableHead>
                <TableHead className="w-[100px]">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {menus && menus.length > 0 ? (
                menus.map((menu) => (
                  <MenuTreeRow key={menu.id} menu={menu} depth={0} onEdit={handleEdit} />
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-muted-foreground">
                    暂无菜单数据
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      )}

      <MenuFormDialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        editingMenu={editingMenu}
        allMenus={menus ?? []}
      />
    </PageContainer>
  )
}

/** 树形菜单行（递归渲染） */
function MenuTreeRow({
  menu,
  depth,
  onEdit
}: {
  menu: MenuVO
  depth: number
  onEdit: (menu: MenuVO) => void
}) {
  const [expanded, setExpanded] = useState(true)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const deleteMutation = useDeleteMenu()
  const hasChildren = menu.children.length > 0

  const handleDelete = useCallback(() => {
    deleteMutation.mutate(menu.id, {
      onSuccess: () => { toast.success("删除成功"); setDeleteOpen(false) },
      onError: (err) => toast.error(`删除失败: ${err.message}`)
    })
  }, [menu, deleteMutation])

  return (
    <>
      <TableRow>
        <TableCell>
          <div className="flex items-center" style={{ paddingLeft: `${depth * 20}px` }}>
            {hasChildren ? (
              <button
                type="button"
                onClick={() => setExpanded(!expanded)}
                className="mr-1 rounded p-0.5 hover:bg-muted"
              >
                {expanded ? (
                  <ChevronDown className="size-4" />
                ) : (
                  <ChevronRight className="size-4" />
                )}
              </button>
            ) : (
              <span className="mr-1 inline-block w-5" />
            )}
            <span className="text-sm">{menu.title}</span>
          </div>
        </TableCell>
        <TableCell className="text-muted-foreground text-sm">{menu.path ?? "—"}</TableCell>
        <TableCell className="text-muted-foreground text-sm">{menu.icon ?? "—"}</TableCell>
        <TableCell>
          <Badge variant="outline" className={cn("text-xs", MENU_TYPE_BADGE[menu.menuType])}>
            {menu.menuType}
          </Badge>
        </TableCell>
        <TableCell className="text-sm">{menu.sortOrder}</TableCell>
        <TableCell>
          <span
            className={cn("text-sm", menu.visible ? "text-green-600" : "text-muted-foreground")}
          >
            {menu.visible ? "是" : "否"}
          </span>
        </TableCell>
        <TableCell>
          <div className="flex gap-1">
            <Button variant="ghost" size="sm" onClick={() => onEdit(menu)}>
              <Pencil className="size-3.5" />
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setDeleteOpen(true)}>
              <Trash2 className="size-3.5 text-destructive" />
            </Button>
            <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>确认删除</AlertDialogTitle>
                  <AlertDialogDescription>
                    确定删除菜单「{menu.title}」？此操作不可撤销。
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel onClick={() => setDeleteOpen(false)}>取消</AlertDialogCancel>
                  <AlertDialogAction onClick={handleDelete}>删除</AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        </TableCell>
      </TableRow>
      {hasChildren &&
        expanded &&
        menu.children
          .sort((a, b) => a.sortOrder - b.sortOrder)
          .map((child) => (
            <MenuTreeRow key={child.id} menu={child} depth={depth + 1} onEdit={onEdit} />
          ))}
    </>
  )
}

/** 新建/编辑菜单 Dialog */
function MenuFormDialog({
  open,
  onOpenChange,
  editingMenu,
  allMenus
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  editingMenu: MenuVO | null
  allMenus: MenuVO[]
}) {
  const createMutation = useCreateMenu()
  const updateMutation = useUpdateMenu()
  const uid = useId()

  const [title, setTitle] = useState("")
  const [path, setPath] = useState("")
  const [icon, setIcon] = useState("")
  const [menuType, setMenuType] = useState<MenuCreateDTO["menuType"]>("MENU")
  const [parentId, setParentId] = useState<string>("none")
  const [sortOrder, setSortOrder] = useState(0)
  const [visible, setVisible] = useState(true)

  /** Dialog 打开时初始化表单 */
  const handleOpenChange = useCallback(
    (nextOpen: boolean) => {
      if (nextOpen) {
        if (editingMenu) {
          setTitle(editingMenu.title)
          setPath(editingMenu.path ?? "")
          setIcon(editingMenu.icon ?? "")
          setMenuType(editingMenu.menuType)
          setParentId(editingMenu.parentId?.toString() ?? "none")
          setSortOrder(editingMenu.sortOrder)
          setVisible(editingMenu.visible)
        } else {
          setTitle("")
          setPath("")
          setIcon("")
          setMenuType("MENU")
          setParentId("none")
          setSortOrder(0)
          setVisible(true)
        }
      }
      onOpenChange(nextOpen)
    },
    [editingMenu, onOpenChange]
  )

  const handleSubmit = useCallback(() => {
    if (!title.trim()) {
      toast.error("标题不能为空")
      return
    }

    const data: MenuCreateDTO = {
      title: title.trim(),
      parentId: parentId === "none" ? null : Number(parentId),
      path: path.trim() || null,
      icon: icon.trim() || null,
      menuType,
      sortOrder,
      visible
    }

    if (editingMenu) {
      updateMutation.mutate(
        { ...data, id: editingMenu.id },
        {
          onSuccess: () => {
            toast.success("更新成功")
            onOpenChange(false)
          },
          onError: (err) => toast.error(`更新失败: ${err.message}`)
        }
      )
    } else {
      createMutation.mutate(data, {
        onSuccess: () => {
          toast.success("创建成功")
          onOpenChange(false)
        },
        onError: (err) => toast.error(`创建失败: ${err.message}`)
      })
    }
  }, [
    title,
    path,
    icon,
    menuType,
    parentId,
    sortOrder,
    visible,
    editingMenu,
    createMutation,
    updateMutation,
    onOpenChange
  ])

  /** 扁平化菜单树用于父级选择 */
  const flatMenus = flattenMenus(allMenus)

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{editingMenu ? "编辑菜单" : "新建菜单"}</DialogTitle>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="grid gap-2">
            <Label htmlFor={`${uid}-title`}>标题</Label>
            <Input
              id={`${uid}-title`}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="菜单标题"
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor={`${uid}-path`}>路径</Label>
            <Input
              id={`${uid}-path`}
              value={path}
              onChange={(e) => setPath(e.target.value)}
              placeholder="/workspace/xxx"
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor={`${uid}-icon`}>图标</Label>
            <Input
              id={`${uid}-icon`}
              value={icon}
              onChange={(e) => setIcon(e.target.value)}
              placeholder="lucide 图标名"
            />
          </div>

          <div className="grid gap-2">
            <Label>类型</Label>
            <Select
              value={menuType}
              onValueChange={(v) => setMenuType(v as MenuCreateDTO["menuType"])}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="GROUP">分组（GROUP）</SelectItem>
                <SelectItem value="MENU">菜单（MENU）</SelectItem>
                <SelectItem value="BUTTON">按钮（BUTTON）</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label>父级菜单</Label>
            <Select value={parentId} onValueChange={(v) => setParentId(v ?? "none")}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="none">无（顶级）</SelectItem>
                {flatMenus
                  .filter((m) => m.id !== editingMenu?.id)
                  .map((m) => (
                    <SelectItem key={m.id} value={m.id.toString()}>
                      {"—".repeat(m.depth)} {m.title}
                    </SelectItem>
                  ))}
              </SelectContent>
            </Select>
          </div>

          <div className="grid gap-2">
            <Label htmlFor={`${uid}-sort`}>排序</Label>
            <Input
              id={`${uid}-sort`}
              type="number"
              value={sortOrder}
              onChange={(e) => setSortOrder(Number(e.target.value))}
            />
          </div>

          <div className="flex items-center gap-2">
            <Switch id={`${uid}-visible`} checked={visible} onCheckedChange={setVisible} />
            <Label htmlFor={`${uid}-visible`}>可见</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={createMutation.isPending || updateMutation.isPending}
          >
            {editingMenu ? "保存" : "创建"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** 扁平化菜单树（用于父级选择下拉） */
function flattenMenus(
  menus: MenuVO[],
  depth = 0
): Array<{ id: number; title: string; depth: number }> {
  const result: Array<{ id: number; title: string; depth: number }> = []
  for (const menu of menus) {
    result.push({ id: menu.id, title: menu.title, depth })
    if (menu.children.length > 0) {
      result.push(...flattenMenus(menu.children, depth + 1))
    }
  }
  return result
}
