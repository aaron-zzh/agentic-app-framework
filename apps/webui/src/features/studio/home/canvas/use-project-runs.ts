/**
 * 项目任务画布数据：AIGC 项目与执行记录，归一成 ProjectRunItem。
 *
 * - 总览：最近的根执行记录（rootOnly）与项目名称；接口失败（如无 AIGC License）或没有记录时用示例数据。
 * - 下钻：一条根执行记录的执行树（/aigc/execution-runs/{id}/tree）；示例数据从示例记录里按父子关系取。
 *
 * @example
 * const overview = useProjectRunsOverview()
 * const tree = useProjectRunTree(rootId, overview.mock)
 * @author AaronZZH & Kiro
 */

"use client"

import { useMemo } from "react"
import {
  type AigcExecutionRun,
  type AigcExecutionRunView,
  type AigcExecutionTargetType,
  useAigcExecutionRuns,
  useAigcExecutionRunTree
} from "@/lib/api/rest/ai/aigc/execution"
import { useAigcProjects } from "@/lib/api/rest/ai/aigc/project"
import { MOCK_PROJECT_RUNS, MOCK_PROJECTS } from "./canvas-mock-data"
import type { ProjectRunItem, ProjectRunProject } from "./types"

const OVERVIEW_PARAMS = { rootOnly: true, pageNo: 1, pageSize: 30 }
const PROJECT_PARAMS = { pageNo: 1, pageSize: 50 }
const TARGET_TYPES: readonly AigcExecutionTargetType[] = ["AGENT", "TOOL", "WORKFLOW", "UNRESOLVED"]

function targetTypeOf(value: string): AigcExecutionTargetType {
  return (TARGET_TYPES as readonly string[]).includes(value)
    ? (value as AigcExecutionTargetType)
    : "UNRESOLVED"
}

function fromRun(run: AigcExecutionRun): ProjectRunItem {
  return {
    id: run.id,
    projectId: run.projectId,
    parentId: run.parentExecutionRunId ?? null,
    title: run.actionKey,
    targetType: run.targetType,
    roleProfileCode: run.roleProfileCode ?? null,
    status: run.status,
    startTime: run.startTime ?? null,
    endTime: run.endTime ?? null
  }
}

/** 执行树视图没有角色与起止时间 */
function fromRunView(run: AigcExecutionRunView): ProjectRunItem {
  return {
    id: run.id,
    projectId: run.projectId,
    parentId: run.parentExecutionRunId ?? null,
    title: run.actionKey,
    targetType: targetTypeOf(run.targetType),
    roleProfileCode: null,
    status: run.status,
    startTime: null,
    endTime: null
  }
}

interface ProjectRunsOverview {
  projects: readonly ProjectRunProject[]
  runs: readonly ProjectRunItem[]
  mock: boolean
  loading: boolean
}

export function useProjectRunsOverview(): ProjectRunsOverview {
  const runs = useAigcExecutionRuns(OVERVIEW_PARAMS)
  const projects = useAigcProjects(PROJECT_PARAMS)

  return useMemo(() => {
    const list = runs.data?.list ?? []
    if (runs.isError || (!runs.isLoading && list.length === 0)) {
      return {
        projects: MOCK_PROJECTS,
        runs: MOCK_PROJECT_RUNS.filter((run) => run.parentId === null),
        mock: true,
        loading: false
      }
    }
    return {
      projects: (projects.data?.list ?? []).map((project) => ({
        id: project.id,
        name: project.name
      })),
      runs: list.map(fromRun),
      mock: false,
      loading: runs.isLoading
    }
  }, [runs.data, runs.isError, runs.isLoading, projects.data])
}

interface ProjectRunTree {
  root: ProjectRunItem | null
  descendants: readonly ProjectRunItem[]
  loading: boolean
  error: boolean
}

/** 示例记录中某条根记录的全部后代 */
function mockDescendants(rootId: number): ProjectRunItem[] {
  const result: ProjectRunItem[] = []
  let frontier = [rootId]
  while (frontier.length > 0) {
    const children = MOCK_PROJECT_RUNS.filter(
      (run) => run.parentId !== null && frontier.includes(run.parentId)
    )
    result.push(...children)
    frontier = children.map((run) => run.id)
  }
  return result
}

export function useProjectRunTree(rootId: number, mock: boolean): ProjectRunTree {
  const tree = useAigcExecutionRunTree(rootId, !mock)

  return useMemo(() => {
    if (mock) {
      return {
        root: MOCK_PROJECT_RUNS.find((run) => run.id === rootId) ?? null,
        descendants: mockDescendants(rootId),
        loading: false,
        error: false
      }
    }
    return {
      root: tree.data ? fromRunView(tree.data.root) : null,
      descendants: (tree.data?.descendants ?? []).map(fromRunView),
      loading: tree.isLoading,
      error: tree.isError
    }
  }, [mock, rootId, tree.data, tree.isLoading, tree.isError])
}
