/**
 * 组织管理页面——基本信息 / 成员管理 / 邀请
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Building2, Trash2, UserPlus } from "lucide-react"
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow
} from "@/components/ui/table"
import { useOrgStore } from "@/lib/store/org-store"
import {
  useAddOrgMember,
  useOrgMembers,
  useOrganizations,
  useRemoveOrgMember,
  useUpdateOrganization
} from "@/lib/queries/use-organizations"

export default function OrganizationSettingsPage() {
  const currentOrgId = useOrgStore((s) => s.currentOrgId)
  const { data: orgs } = useOrganizations()
  const org = orgs?.find((o) => o.id === currentOrgId) ?? orgs?.[0]

  if (!org) {
    return <div className="p-6 text-muted-foreground">暂无组织信息</div>
  }

  return (
    <div className="mx-auto max-w-3xl space-y-8 p-6">
      <h1 className="flex items-center gap-2 font-semibold text-xl">
        <Building2 className="size-5" />
        组织设置
      </h1>
      <OrgInfoSection orgId={org.id} name={org.name} slug={org.slug} logo={org.logo} />
      <MembersSection orgId={org.id} />
    </div>
  )
}

/** 基本信息编辑 */
function OrgInfoSection({
  orgId,
  name,
  slug,
  logo
}: {
  orgId: string
  name: string
  slug: string
  logo?: string
}) {
  const [form, setForm] = useState({ name, slug })
  const updateOrg = useUpdateOrganization()

  function handleSave() {
    updateOrg.mutate({ id: orgId, data: form })
  }

  return (
    <section className="space-y-4 rounded-lg border p-4">
      <h2 className="font-medium text-base">基本信息</h2>
      <div className="flex items-center gap-4">
        <Avatar className="size-14 rounded-lg after:hidden">
          <AvatarImage src={logo} alt={name} className="object-cover" />
          <AvatarFallback className="rounded-lg bg-primary/10 font-bold text-primary text-lg">
            {name.slice(0, 1)}
          </AvatarFallback>
        </Avatar>
        <div className="flex-1 space-y-2">
          <div>
            <label htmlFor="org-name" className="mb-1 block text-muted-foreground text-sm">
              组织名称
            </label>
            <Input
              id="org-name"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
            />
          </div>
          <div>
            <label htmlFor="org-slug" className="mb-1 block text-muted-foreground text-sm">
              标识（URL）
            </label>
            <Input
              id="org-slug"
              value={form.slug}
              onChange={(e) => setForm((f) => ({ ...f, slug: e.target.value }))}
            />
          </div>
        </div>
      </div>
      <Button onClick={handleSave} disabled={updateOrg.isPending} size="sm">
        保存
      </Button>
    </section>
  )
}

/** 成员管理 */
function MembersSection({ orgId }: { orgId: string }) {
  const { data: members } = useOrgMembers(orgId)
  const removeMember = useRemoveOrgMember(orgId)

  return (
    <section className="space-y-4 rounded-lg border p-4">
      <div className="flex items-center justify-between">
        <h2 className="font-medium text-base">成员管理</h2>
        <InviteMemberDialog orgId={orgId} />
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>用户</TableHead>
            <TableHead>角色</TableHead>
            <TableHead>加入时间</TableHead>
            <TableHead className="w-16">操作</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {members?.map((m) => (
            <TableRow key={m.userId}>
              <TableCell className="flex items-center gap-2">
                <Avatar className="size-7 after:hidden">
                  <AvatarImage src={m.avatar} alt={m.nickname} />
                  <AvatarFallback className="text-xs">{m.nickname.slice(0, 1)}</AvatarFallback>
                </Avatar>
                <span>{m.nickname}</span>
              </TableCell>
              <TableCell>{m.role}</TableCell>
              <TableCell>{m.joinedAt}</TableCell>
              <TableCell>
                {m.role !== "owner" && (
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => removeMember.mutate(m.userId)}
                    disabled={removeMember.isPending}
                  >
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}

/** 邀请成员对话框 */
function InviteMemberDialog({ orgId }: { orgId: string }) {
  const [open, setOpen] = useState(false)
  const [userId, setUserId] = useState("")
  const addMember = useAddOrgMember(orgId)

  function handleInvite() {
    if (!userId.trim()) return
    addMember.mutate({ userId, role: "member" }, { onSuccess: () => { setOpen(false); setUserId("") } })
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" variant="outline">
          <UserPlus className="mr-1.5 size-4" />
          邀请成员
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>邀请成员</DialogTitle>
        </DialogHeader>
        <div className="space-y-2 py-4">
          <label htmlFor="invite-user" className="text-muted-foreground text-sm">
            用户 ID
          </label>
          <Input
            id="invite-user"
            placeholder="输入用户 ID"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
          />
        </div>
        <DialogFooter>
          <Button onClick={handleInvite} disabled={addMember.isPending || !userId.trim()}>
            确认邀请
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
