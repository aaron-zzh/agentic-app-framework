"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CheckCircle2, Circle, Loader2, Play, Rocket, XCircle } from "lucide-react"

import { Button } from "@/components/ui/button"
import { apiClient } from "@/lib/api/client"

interface BuildStatus {
  runId: number
  status: string
  conclusion: string | null
  htmlUrl: string
  updatedAt: string
}

function useRecentBuilds() {
  return useQuery<BuildStatus[]>({
    queryKey: ["ci-builds"],
    queryFn: () => apiClient.get("/api/autodev/git/ci/recent?limit=20").then((r) => r.data),
    refetchInterval: 10000
  })
}

export default function CiStatusPage() {
  const { data: builds, isLoading } = useRecentBuilds()
  const qc = useQueryClient()

  const triggerMutation = useMutation({
    mutationFn: (data: { workflow: string; ref: string }) =>
      apiClient.post("/api/autodev/git/ci/trigger", { ...data, inputs: {} }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ci-builds"] })
  })

  const deployMutation = useMutation({
    mutationFn: (data: { environment: string; ref: string }) =>
      apiClient.post("/api/autodev/git/ci/deploy", data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["ci-builds"] })
  })

  return (
    <div className="space-y-4 p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-semibold text-xl">CI/CD 状态</h1>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => triggerMutation.mutate({ workflow: "ci.yml", ref: "main" })}
            disabled={triggerMutation.isPending}
          >
            <Play className="mr-1 size-3" />
            触发 CI
          </Button>
          <Button
            size="sm"
            onClick={() => deployMutation.mutate({ environment: "staging", ref: "main" })}
            disabled={deployMutation.isPending}
          >
            <Rocket className="mr-1 size-3" />
            部署 Staging
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="text-muted-foreground">加载中...</div>
      ) : (
        <div className="space-y-2">
          {builds?.map((build) => (
            <BuildCard key={build.runId} build={build} />
          ))}
          {builds?.length === 0 && (
            <p className="text-center text-muted-foreground">暂无构建记录</p>
          )}
        </div>
      )}
    </div>
  )
}

function BuildCard({ build }: { build: BuildStatus }) {
  const icon = (() => {
    if (build.status === "in_progress") return <Loader2 className="size-4 animate-spin text-blue-500" />
    if (build.conclusion === "success") return <CheckCircle2 className="size-4 text-green-500" />
    if (build.conclusion === "failure") return <XCircle className="size-4 text-red-500" />
    return <Circle className="size-4 text-muted-foreground" />
  })()

  return (
    <a
      href={build.htmlUrl}
      target="_blank"
      rel="noopener noreferrer"
      className="flex items-center gap-3 rounded-lg border p-3 transition-colors hover:bg-accent"
    >
      {icon}
      <div className="flex-1">
        <span className="font-medium text-sm">Run #{build.runId}</span>
        <span className="ml-2 text-muted-foreground text-xs">
          {build.status}{build.conclusion ? ` · ${build.conclusion}` : ""}
        </span>
      </div>
      <span className="text-muted-foreground text-xs">{build.updatedAt?.slice(0, 16)}</span>
    </a>
  )
}
