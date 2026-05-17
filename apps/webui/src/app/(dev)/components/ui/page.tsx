"use client"

import { useId, useState } from "react"
import { ComponentBox, ComponentLayout } from "@/components/common/ComponentLayout"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Progress } from "@/components/ui/progress"
import {
  Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"

export default function UiPage() {
  const uid = useId()
  const [checked, setChecked] = useState(false)
  const [switched, setSwitched] = useState(false)
  const [selectVal, setSelectVal] = useState("")
  const [progress, setProgress] = useState(40)

  return (
    <ComponentLayout
      heading="UI 基础组件"
      description="shadcn/ui 原语组件，基于 Base UI + Tailwind CSS。"
      links={[{ name: "shadcn/ui", href: "https://ui.shadcn.com/docs/components" }]}
      sectionData={[
        {
          name: "Button",
          component: (
            <ComponentBox>
              <Button>默认</Button>
              <Button variant="secondary">次要</Button>
              <Button variant="outline">描边</Button>
              <Button variant="ghost">幽灵</Button>
              <Button variant="destructive">危险</Button>
              <Button disabled>禁用</Button>
              <Button size="sm">小号</Button>
              <Button size="lg">大号</Button>
            </ComponentBox>
          )
        },
        {
          name: "Badge",
          component: (
            <ComponentBox>
              <Badge>默认</Badge>
              <Badge variant="secondary">次要</Badge>
              <Badge variant="outline">描边</Badge>
              <Badge variant="destructive">危险</Badge>
            </ComponentBox>
          )
        },
        {
          name: "Input / Label",
          component: (
            <ComponentBox className="gap-4">
              <div className="flex w-48 flex-col gap-1.5">
                <Label htmlFor={`${uid}-a`}>标签</Label>
                <Input id={`${uid}-a`} placeholder="请输入..." />
              </div>
              <div className="flex w-48 flex-col gap-1.5">
                <Label htmlFor={`${uid}-b`}>错误状态</Label>
                <Input id={`${uid}-b`} aria-invalid placeholder="错误输入" />
              </div>
              <div className="flex w-48 flex-col gap-1.5">
                <Label>禁用</Label>
                <Input disabled placeholder="禁用输入" />
              </div>
            </ComponentBox>
          )
        },
        {
          name: "Textarea",
          component: (
            <ComponentBox>
              <Textarea className="w-64" placeholder="多行文本..." />
              <Textarea className="w-64" disabled placeholder="禁用" />
            </ComponentBox>
          )
        },
        {
          name: "Select",
          component: (
            <ComponentBox>
              <Select value={selectVal} onValueChange={(v) => setSelectVal(v ?? "")}>
                <SelectTrigger className="w-40">
                  <SelectValue placeholder="请选择" />
                </SelectTrigger>
                <SelectContent>
                  <SelectGroup>
                    <SelectItem value="draft">草稿</SelectItem>
                    <SelectItem value="published">已发布</SelectItem>
                    <SelectItem value="archived">已归档</SelectItem>
                  </SelectGroup>
                </SelectContent>
              </Select>
              <span className="text-muted-foreground text-sm">当前值：{selectVal || "—"}</span>
            </ComponentBox>
          )
        },
        {
          name: "Checkbox",
          component: (
            <ComponentBox>
              <div className="flex items-center gap-2">
                <Checkbox id={`${uid}-c1`} checked={checked} onCheckedChange={(v) => setChecked(v === true)} />
                <Label htmlFor={`${uid}-c1`}>复选框（{checked ? "✓" : "○"}）</Label>
              </div>
              <div className="flex items-center gap-2">
                <Checkbox id={`${uid}-c2`} disabled />
                <Label htmlFor={`${uid}-c2`}>禁用</Label>
              </div>
            </ComponentBox>
          )
        },
        {
          name: "Switch",
          component: (
            <ComponentBox>
              <div className="flex items-center gap-2">
                <Switch id={`${uid}-s1`} checked={switched} onCheckedChange={setSwitched} />
                <Label htmlFor={`${uid}-s1`}>开关（{switched ? "开" : "关"}）</Label>
              </div>
              <div className="flex items-center gap-2">
                <Switch id={`${uid}-s2`} disabled />
                <Label htmlFor={`${uid}-s2`}>禁用</Label>
              </div>
            </ComponentBox>
          )
        },
        {
          name: "Tooltip",
          component: (
            <ComponentBox>
              <Tooltip>
                <TooltipTrigger>悬停查看提示</TooltipTrigger>
                <TooltipContent>这是一个提示</TooltipContent>
              </Tooltip>
            </ComponentBox>
          )
        },
        {
          name: "Progress",
          component: (
            <ComponentBox className="flex-col gap-3">
              <Progress value={progress} className="w-64" />
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => setProgress(Math.max(0, progress - 10))}>-10</Button>
                <Button size="sm" variant="outline" onClick={() => setProgress(Math.min(100, progress + 10))}>+10</Button>
                <span className="text-muted-foreground text-sm">{progress}%</span>
              </div>
            </ComponentBox>
          )
        },
        {
          name: "Skeleton",
          component: (
            <ComponentBox className="flex-col items-start gap-2">
              <Skeleton className="h-4 w-48" />
              <Skeleton className="h-4 w-32" />
              <Skeleton className="h-8 w-64" />
            </ComponentBox>
          )
        },
        {
          name: "Tabs",
          component: (
            <ComponentBox>
              <Tabs defaultValue="tab1" className="w-80">
                <TabsList>
                  <TabsTrigger value="tab1">标签一</TabsTrigger>
                  <TabsTrigger value="tab2">标签二</TabsTrigger>
                  <TabsTrigger value="tab3" disabled>禁用</TabsTrigger>
                </TabsList>
                <TabsContent value="tab1" className="p-3 text-sm">标签一的内容</TabsContent>
                <TabsContent value="tab2" className="p-3 text-sm">标签二的内容</TabsContent>
              </Tabs>
            </ComponentBox>
          )
        }
      ]}
    />
  )
}
