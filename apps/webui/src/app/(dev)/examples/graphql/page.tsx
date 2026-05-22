"use client"

/**
 * GraphQL 示例页——通过 fetch 调用后端 /graphql 端点
 * 路由：/dev/examples/graphql
 * @author AaronZZH & Kiro
 */

import { useCallback, useState } from "react"
import { PageContainer } from "@/components/common/PageContainer"
import { TypographyH1, TypographyMuted } from "@/components/ui/typography"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? ""

interface Person {
  name: string
  born?: number
}

interface Movie {
  title: string
  tagline?: string
  released?: number
  votes?: number
  actors?: Person[]
}

interface GraphQLResponse<T> {
  data?: T
  errors?: { message: string }[]
}

/** 通用 GraphQL 请求 */
async function gqlFetch<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
  const res = await fetch(`${API_URL}/graphql`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ query, variables }),
  })
  const json = (await res.json()) as GraphQLResponse<T>
  if (json.errors?.length) throw new Error(json.errors[0].message)
  if (!json.data) throw new Error("无数据返回")
  return json.data
}

export default function GraphQLExamplePage() {
  const [keyword, setKeyword] = useState("")
  const [movies, setMovies] = useState<Movie[]>([])
  const [selected, setSelected] = useState<Movie | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  /** 搜索电影 */
  const handleSearch = useCallback(async () => {
    setLoading(true)
    setError("")
    setSelected(null)

    try {
      const query = keyword.trim()
        ? `query($title: String!) { searchMovies(title: $title) { title tagline released } }`
        : `query { movies { title tagline released } }`

      const variables = keyword.trim() ? { title: keyword } : undefined
      const data = await gqlFetch<{ movies?: Movie[]; searchMovies?: Movie[] }>(
        query,
        variables
      )
      setMovies(data.searchMovies ?? data.movies ?? [])
    } catch (e) {
      setError(e instanceof Error ? e.message : "查询失败")
    } finally {
      setLoading(false)
    }
  }, [keyword])

  /** 查看电影详情 */
  const handleDetail = useCallback(async (title: string) => {
    try {
      const data = await gqlFetch<{ movie: Movie }>(
        `query($title: String!) { movie(title: $title) { title tagline released votes actors { name born } } }`,
        { title }
      )
      setSelected(data.movie)
    } catch (e) {
      setError(e instanceof Error ? e.message : "查询详情失败")
    }
  }, [])

  /** 投票 */
  const handleVote = useCallback(async (title: string) => {
    try {
      await gqlFetch<{ vote: boolean }>(
        `mutation($title: String!) { vote(title: $title) }`,
        { title }
      )
      // 刷新详情
      await handleDetail(title)
    } catch (e) {
      setError(e instanceof Error ? e.message : "投票失败")
    }
  }, [handleDetail])

  return (
    <PageContainer maxWidth="md">
      <div className="mb-6 space-y-2">
        <TypographyH1>GraphQL 示例</TypographyH1>
        <TypographyMuted>
          需要后端启用 Neo4j 并导入示例数据
        </TypographyMuted>
      </div>

      {/* 搜索栏 */}
      <div className="mb-4 flex gap-2">
        <Input
          placeholder="输入电影关键词搜索（留空查询全部）"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSearch()}
        />
        <Button onClick={handleSearch} disabled={loading}>
          {loading ? "搜索中..." : "搜索"}
        </Button>
      </div>

      {error && <p className="mb-4 text-destructive text-sm">{error}</p>}

      <div className="grid gap-4 lg:grid-cols-2">
        {/* 电影列表 */}
        <div className="space-y-3">
          {movies.length === 0 && !loading && (
            <p className="text-muted-foreground text-sm">点击搜索查询电影列表</p>
          )}
          {movies.map((movie) => (
            <Card
              key={movie.title}
              size="sm"
              className="cursor-pointer transition-colors hover:border-primary/50"
              onClick={() => handleDetail(movie.title)}
            >
              <CardContent className="p-3">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-medium text-sm">{movie.title}</p>
                    {movie.tagline && (
                      <p className="mt-0.5 text-muted-foreground text-xs">{movie.tagline}</p>
                    )}
                  </div>
                  {movie.released && (
                    <Badge variant="secondary">{movie.released}</Badge>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* 电影详情 */}
        {selected && (
          <Card>
            <CardHeader>
              <CardTitle>{selected.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              {selected.tagline && (
                <p className="text-muted-foreground text-sm italic">
                  &ldquo;{selected.tagline}&rdquo;
                </p>
              )}
              <div className="flex gap-2">
                {selected.released && (
                  <Badge variant="secondary">上映：{selected.released}</Badge>
                )}
                {selected.votes != null && (
                  <Badge variant="outline">票数：{selected.votes}</Badge>
                )}
              </div>

              <Button
                size="sm"
                variant="outline"
                onClick={() => handleVote(selected.title)}
              >
                👍 投票
              </Button>

              {selected.actors && selected.actors.length > 0 && (
                <div>
                  <p className="mb-1 font-medium text-xs">
                    演员（{selected.actors.length}）
                  </p>
                  <ul className="space-y-1">
                    {selected.actors.map((actor) => (
                      <li
                        key={actor.name}
                        className="text-muted-foreground text-xs"
                      >
                        {actor.name}
                        {actor.born && ` (${actor.born})`}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </CardContent>
          </Card>
        )}
      </div>
    </PageContainer>
  )
}
