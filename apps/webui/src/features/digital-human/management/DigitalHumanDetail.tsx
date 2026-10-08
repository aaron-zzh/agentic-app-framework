/**
 * 数字人详情页
 * @author Kiro
 */

"use client"

import { Archive, ArchiveRestore, Link, Pencil, Trash, Video } from "lucide-react"
import Image from "next/image"
import { useRouter } from "next/navigation"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { VideoGenerationDialog, VideoHistoryList } from "@/features/digital-human/video"
import {
  useArchiveDigitalHuman,
  useDeleteDigitalHuman,
  useDigitalHuman,
  useRestoreDigitalHuman,
  useUnlinkAssistant
} from "@/lib/api/rest/digital-human"
import { useDigitalHumanVideos } from "@/lib/api/rest/digital-human-video"

interface DigitalHumanDetailProps {
  id: string
}

export default function DigitalHumanDetail({ id }: DigitalHumanDetailProps) {
  const router = useRouter()
  const { data: digitalHuman, isLoading } = useDigitalHuman(id)
  const { data: videosData, isLoading: videosLoading } = useDigitalHumanVideos(id)
  const deleteMutation = useDeleteDigitalHuman()
  const archiveMutation = useArchiveDigitalHuman()
  const restoreMutation = useRestoreDigitalHuman()
  const unlinkMutation = useUnlinkAssistant()

  if (isLoading) {
    return (
      <div className="container py-6">
        <div className="h-96 animate-pulse rounded-lg bg-muted" />
      </div>
    )
  }

  if (!digitalHuman) {
    return (
      <div className="container py-12 text-center">
        <p className="text-muted-foreground">数字人不存在</p>
      </div>
    )
  }

  const canEdit = digitalHuman.ownedByCurrentUser
  const isArchived = digitalHuman.status === "archived"
  const tags = digitalHuman.tags ?? []
  const appearance = digitalHuman.appearance

  const handleDelete = async () => {
    if (!confirm("确认删除此数字人？删除后无法恢复。")) return
    await deleteMutation.mutateAsync(id)
    router.push("/digital-humans")
  }

  const handleArchive = async () => {
    if (!confirm("确认归档此数字人？归档后将不再显示在列表中。")) return
    await archiveMutation.mutateAsync(id)
  }

  const handleRestore = async () => {
    await restoreMutation.mutateAsync(id)
  }

  const handleUnlink = async () => {
    if (!confirm("确认取消关联助理？")) return
    await unlinkMutation.mutateAsync(id)
  }

  return (
    <div className="container py-6">
      {/* 标题栏 */}
      <div className="mb-6 flex items-start justify-between">
        <div className="flex items-start gap-4">
          <Image
            src={digitalHuman.avatarImageUrl}
            alt={digitalHuman.name}
            width={96}
            height={96}
            className="size-24 rounded-lg border object-cover"
          />
          <div>
            <div className="mb-2 flex items-center gap-2">
              <h1 className="font-semibold text-2xl">{digitalHuman.name}</h1>
              {isArchived && <Badge variant="outline">已归档</Badge>}
            </div>
            {digitalHuman.description && (
              <p className="mb-2 text-muted-foreground">{digitalHuman.description}</p>
            )}
            {tags.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {tags.map((tag) => (
                  <Badge key={tag} variant="secondary">
                    {tag}
                  </Badge>
                ))}
              </div>
            )}
          </div>
        </div>

        {canEdit && (
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => router.push(`/digital-humans/${id}/edit`)}>
              <Pencil className="mr-2 size-4" />
              编辑
            </Button>
            {isArchived ? (
              <Button variant="outline" onClick={handleRestore}>
                <ArchiveRestore className="mr-2 size-4" />
                恢复
              </Button>
            ) : (
              <Button variant="outline" onClick={handleArchive}>
                <Archive className="mr-2 size-4" />
                归档
              </Button>
            )}
            <Button variant="destructive" onClick={handleDelete}>
              <Trash className="mr-2 size-4" />
              删除
            </Button>
          </div>
        )}
      </div>

      <Tabs defaultValue="overview" className="space-y-6">
        <TabsList>
          <TabsTrigger value="overview">概览</TabsTrigger>
          <TabsTrigger value="videos">视频历史</TabsTrigger>
          <TabsTrigger value="voice">音色配置</TabsTrigger>
          <TabsTrigger value="appearance">外观配置</TabsTrigger>
          <TabsTrigger value="usage">使用统计</TabsTrigger>
        </TabsList>

        {/* 概览 */}
        <TabsContent value="overview" className="space-y-6">
          {/* 关联助理 */}
          <Card>
            <CardHeader>
              <CardTitle>关联助理</CardTitle>
            </CardHeader>
            <CardContent>
              {digitalHuman.assistantId ? (
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">已关联助理 ID: {digitalHuman.assistantId}</p>
                    {digitalHuman.syncModelConfig && (
                      <p className="text-muted-foreground text-sm">已同步助理模型配置</p>
                    )}
                  </div>
                  {canEdit && (
                    <div className="flex gap-2">
                      <Button variant="outline" onClick={handleUnlink}>
                        取消关联
                      </Button>
                      <Button
                        onClick={() => router.push(`/assistants/${digitalHuman.assistantId}`)}
                      >
                        查看助理
                      </Button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="py-4 text-center">
                  <p className="mb-4 text-muted-foreground text-sm">暂未关联助理</p>
                  {canEdit && (
                    <Button
                      variant="outline"
                      onClick={() => router.push(`/digital-humans/${id}/link-assistant`)}
                    >
                      <Link className="mr-2 size-4" />
                      关联助理
                    </Button>
                  )}
                </div>
              )}
            </CardContent>
          </Card>

          {/* 快速操作 */}
          <Card>
            <CardHeader>
              <CardTitle>快速操作</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <VideoGenerationDialog
                digitalHumanId={id}
                digitalHumanName={digitalHuman.name}
                trigger={
                  <Button className="w-full justify-start" variant="outline">
                    <Video className="mr-2 size-4" />
                    生成视频
                  </Button>
                }
              />
              {canEdit && (
                <Button
                  className="w-full justify-start"
                  variant="outline"
                  onClick={() => router.push(`/digital-humans/${id}/change-voice`)}
                >
                  切换音色
                </Button>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* 视频历史 */}
        <TabsContent value="videos">
          <VideoHistoryList
            videos={
              videosData?.content.map((v) => ({
                id: v.id,
                title: v.title,
                url: v.videoUrl,
                thumbnailUrl: v.thumbnailUrl,
                duration: v.duration,
                createdAt: v.createdAt,
                projectId: v.projectId
              })) || []
            }
            loading={videosLoading}
          />
        </TabsContent>

        {/* 音色配置 */}
        <TabsContent value="voice">
          <Card>
            <CardHeader>
              <CardTitle>音色配置</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <div className="mb-1 font-medium text-sm">音色名称</div>
                <div>{digitalHuman.voiceConfig.voiceName}</div>
              </div>
              <Separator />
              <div>
                <div className="mb-1 font-medium text-sm">语速</div>
                <div>{digitalHuman.voiceConfig.speechRate}</div>
              </div>
              <Separator />
              <div>
                <div className="mb-1 font-medium text-sm">音调</div>
                <div>{digitalHuman.voiceConfig.pitchRate}</div>
              </div>
              <Separator />
              <div>
                <div className="mb-1 font-medium text-sm">音量</div>
                <div>{digitalHuman.voiceConfig.volume}</div>
              </div>
            </CardContent>
          </Card>
        </TabsContent>

        {/* 外观配置 */}
        <TabsContent value="appearance">
          <Card>
            <CardHeader>
              <CardTitle>外观配置</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {appearance ? (
                <>
                  <div>
                    <div className="mb-1 font-medium text-sm">性别</div>
                    <div>
                      {appearance.gender === "female"
                        ? "女性"
                        : appearance.gender === "male"
                          ? "男性"
                          : "中性"}
                    </div>
                  </div>
                  <Separator />
                  {appearance.age && (
                    <>
                      <div>
                        <div className="mb-1 font-medium text-sm">年龄</div>
                        <div>{appearance.age}</div>
                      </div>
                      <Separator />
                    </>
                  )}
                  <div>
                    <div className="mb-1 font-medium text-sm">风格</div>
                    <div>{appearance.style}</div>
                  </div>
                  <Separator />
                  <div>
                    <div className="mb-1 font-medium text-sm">生成提示词</div>
                    <div className="text-muted-foreground text-sm">{appearance.prompt}</div>
                  </div>
                </>
              ) : (
                <p className="text-muted-foreground text-sm">暂无外观配置</p>
              )}
            </CardContent>
          </Card>
        </TabsContent>
        {/* 使用统计 */}
        <TabsContent value="usage">
          <Card>
            <CardHeader>
              <CardTitle>使用统计</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-3 gap-4">
                <div className="rounded-lg border p-4">
                  <div className="mb-1 text-muted-foreground text-sm">视频数量</div>
                  <div className="font-semibold text-2xl">{digitalHuman.videoCount}</div>
                </div>
                <div className="rounded-lg border p-4">
                  <div className="mb-1 text-muted-foreground text-sm">总时长（秒）</div>
                  <div className="font-semibold text-2xl">{digitalHuman.totalDuration}</div>
                </div>
                <div className="rounded-lg border p-4">
                  <div className="mb-1 text-muted-foreground text-sm">消耗积分</div>
                  <div className="font-semibold text-2xl">{digitalHuman.creditsConsumed}</div>
                </div>
              </div>
              {digitalHuman.lastUsedAt && (
                <>
                  <Separator />
                  <div>
                    <div className="mb-1 font-medium text-sm">最后使用时间</div>
                    <div className="text-muted-foreground text-sm">
                      {new Date(digitalHuman.lastUsedAt).toLocaleString("zh-CN")}
                    </div>
                  </div>
                </>
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
