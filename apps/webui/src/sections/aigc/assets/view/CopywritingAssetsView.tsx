/**
 * /studio/assets/copywriting——文案资产查询与项目关联视图。
 * @author AaronZZH & Kiro
 */

"use client"

import { CircleAlert, FileText, Search } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { useDebounce } from "use-debounce"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle
} from "@/components/ui/empty"
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Skeleton } from "@/components/ui/skeleton"
import {
  type CopywritingAssetLinkStatus,
  useAigcProjects,
  useCopywritingAssets
} from "@/lib/api/rest/ai"

const PAGE_SIZE = 12
const ALL_PROJECTS = "ALL"

const LINK_STATUS_LABELS: Record<CopywritingAssetLinkStatus, string> = {
  ALL: "全部关联状态",
  LINKED: "已关联项目",
  UNLINKED: "独立文案"
}

const DATE_FORMATTER = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit"
})

function formatUpdateTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : DATE_FORMATTER.format(date)
}

export function CopywritingAssetsView() {
  const [keyword, setKeyword] = useState("")
  const [debouncedKeyword] = useDebounce(keyword.trim(), 300)
  const [linkStatus, setLinkStatus] = useState<CopywritingAssetLinkStatus>("ALL")
  const [selectedProject, setSelectedProject] = useState(ALL_PROJECTS)
  const [pageNo, setPageNo] = useState(1)
  const projectsQuery = useAigcProjects({ pageNo: 1, pageSize: -1 })
  const selectedProjectId = selectedProject === ALL_PROJECTS ? undefined : Number(selectedProject)
  const assetsQuery = useCopywritingAssets({
    linkStatus,
    projectId: selectedProjectId,
    keyword: debouncedKeyword || undefined,
    pageNo,
    pageSize: PAGE_SIZE
  })
  const currentPage = assetsQuery.data?.pageNo ?? pageNo
  const totalPages = Math.max(1, Math.ceil((assetsQuery.data?.total ?? 0) / PAGE_SIZE))

  function handleLinkStatusChange(value: string | null) {
    if (value !== "ALL" && value !== "LINKED" && value !== "UNLINKED") return
    setLinkStatus(value)
    if (value === "UNLINKED") setSelectedProject(ALL_PROJECTS)
    setPageNo(1)
  }

  function handleProjectChange(value: string | null) {
    if (value === null) return
    setSelectedProject(value)
    if (value !== ALL_PROJECTS) setLinkStatus("LINKED")
    setPageNo(1)
  }

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-5 p-6">
      <header>
        <h1 className="font-semibold text-xl">文案资产</h1>
        <p className="mt-1 text-muted-foreground text-sm">
          查找已手动保存的文案，并按项目关联状态集中管理。
        </p>
      </header>

      <div className="grid gap-3 md:grid-cols-[minmax(0,1fr)_12rem_14rem]">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={keyword}
            onChange={(event) => {
              setKeyword(event.target.value)
              setPageNo(1)
            }}
            placeholder="搜索标题或摘要"
            aria-label="搜索文案资产"
            className="pl-9"
          />
        </div>

        <Select value={linkStatus} onValueChange={handleLinkStatusChange}>
          <SelectTrigger className="w-full" aria-label="关联状态">
            <SelectValue>{LINK_STATUS_LABELS[linkStatus]}</SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value="ALL">全部关联状态</SelectItem>
              <SelectItem value="LINKED">已关联项目</SelectItem>
              <SelectItem value="UNLINKED">独立文案</SelectItem>
            </SelectGroup>
          </SelectContent>
        </Select>

        <Select
          value={selectedProject}
          onValueChange={handleProjectChange}
          disabled={linkStatus === "UNLINKED" || projectsQuery.isLoading || projectsQuery.isError}
        >
          <SelectTrigger className="w-full" aria-label="具体项目">
            <SelectValue>
              {selectedProject === ALL_PROJECTS
                ? "全部项目"
                : (projectsQuery.data?.list.find((project) => project.id === selectedProjectId)
                    ?.name ?? "选择项目")}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL_PROJECTS}>全部项目</SelectItem>
              {(projectsQuery.data?.list ?? []).map((project) => (
                <SelectItem key={project.id} value={String(project.id)}>
                  {project.name}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>

      {projectsQuery.isError ? (
        <div
          role="alert"
          className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm"
        >
          <span className="flex items-center gap-2 text-destructive">
            <CircleAlert className="size-4" />
            项目列表加载失败，暂时无法按项目筛选。
          </span>
          <Button
            variant="outline"
            size="xs"
            disabled={projectsQuery.isFetching}
            onClick={() => projectsQuery.refetch()}
          >
            {projectsQuery.isFetching ? "重试中..." : "重试"}
          </Button>
        </div>
      ) : null}

      {assetsQuery.isLoading ? (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {["one", "two", "three", "four", "five", "six"].map((key) => (
            <Skeleton key={key} className="h-52 rounded-xl" />
          ))}
        </div>
      ) : assetsQuery.isError ? (
        <Empty className="min-h-72 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <CircleAlert />
            </EmptyMedia>
            <EmptyTitle>文案资产加载失败</EmptyTitle>
            <EmptyDescription>请检查网络连接后重试。</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button variant="outline" onClick={() => assetsQuery.refetch()}>
              重新加载
            </Button>
          </EmptyContent>
        </Empty>
      ) : (assetsQuery.data?.list.length ?? 0) === 0 ? (
        <Empty className="min-h-72 border">
          <EmptyHeader>
            <EmptyMedia variant="icon">
              <FileText />
            </EmptyMedia>
            <EmptyTitle>{debouncedKeyword ? "没有匹配的文案" : "暂无文案资产"}</EmptyTitle>
            <EmptyDescription>
              {debouncedKeyword
                ? "尝试更换关键词或调整关联筛选条件。"
                : "在文案创作页手动保存后，文案会出现在这里。"}
            </EmptyDescription>
          </EmptyHeader>
        </Empty>
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {assetsQuery.data?.list.map((asset) => (
            <Card key={asset.documentId} className="min-w-0">
              <CardHeader>
                <CardTitle className="line-clamp-2 text-base">{asset.title}</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-1 flex-col gap-4">
                <p className="line-clamp-3 text-muted-foreground text-sm leading-6">
                  {asset.summary || "暂无摘要"}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {asset.projects.length > 0 ? (
                    asset.projects.map((project) => (
                      <Badge key={project.projectId} variant="secondary">
                        {project.projectName}
                      </Badge>
                    ))
                  ) : (
                    <Badge variant="outline">独立文案</Badge>
                  )}
                </div>
              </CardContent>
              <CardFooter className="justify-between gap-3">
                <time className="text-muted-foreground text-xs" dateTime={asset.updateTime}>
                  更新于 {formatUpdateTime(asset.updateTime)}
                </time>
                <Button
                  nativeButton={false}
                  render={<Link href={`/docs/${asset.documentId}/edit`} />}
                  variant="outline"
                  size="xs"
                >
                  编辑
                </Button>
              </CardFooter>
            </Card>
          ))}
        </div>
      )}

      {!assetsQuery.isLoading && !assetsQuery.isError && (assetsQuery.data?.total ?? 0) > 0 ? (
        <footer className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-muted-foreground text-sm">
            共 {assetsQuery.data?.total ?? 0} 条 · 第 {currentPage}/{totalPages} 页
          </p>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={currentPage <= 1 || assetsQuery.isFetching}
              onClick={() => setPageNo((current) => Math.max(1, current - 1))}
            >
              上一页
            </Button>
            <Button
              variant="outline"
              size="sm"
              disabled={!assetsQuery.data?.hasMore || assetsQuery.isFetching}
              onClick={() => setPageNo((current) => current + 1)}
            >
              下一页
            </Button>
          </div>
        </footer>
      ) : null}
    </div>
  )
}
