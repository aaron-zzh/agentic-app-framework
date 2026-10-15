/**
 * 助理角色设置视图：角色库（绑定与排序默认）与我的私有角色两个标签页。
 * @author AaronZZH & Kiro
 */

"use client"

import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"

import { MyRolesPanel } from "./MyRolesPanel"
import { RoleLibraryPanel } from "./RoleLibraryPanel"

export function AssistantRolesView() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="font-semibold text-2xl">助理角色</h1>
        <p className="text-muted-foreground text-sm">
          从角色库选择助理要使用的角色，或创建只属于你的私有角色
        </p>
      </div>
      <Tabs defaultValue="library">
        <TabsList>
          <TabsTrigger value="library">角色库</TabsTrigger>
          <TabsTrigger value="mine">我的角色</TabsTrigger>
        </TabsList>
        <TabsContent value="library" className="pt-4">
          <RoleLibraryPanel />
        </TabsContent>
        <TabsContent value="mine" className="pt-4">
          <MyRolesPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}
