/**
 * 通用分页 composable
 * 基于 alova usePagination，封装 AAF 标准分页接口
 *
 * 用法：
 *   const { list, loading, loadMore, refresh } = usePage('/admin/user/page', { status: 1 })
 *
 * 后端接口约定：
 *   GET /xxx/page?pageNo=1&pageSize=20&...params
 *   返回：{ code: 0, data: { list: T[], total: number } }
 */
import { usePagination } from 'alova/client'
import { alovaInstance } from '@/api/core/instance'

export interface PageResult<T> {
  list: T[]
  total: number
}

export function usePage<T = Record<string, unknown>>(
  /** 接口路径，如 '/admin/user/page' */
  path: string,
  /** 额外查询参数（响应式或普通对象） */
  params: Record<string, unknown> = {},
  options: {
    pageSize?: number
    immediate?: boolean
  } = {},
) {
  const { pageSize = 20, immediate = true } = options

  const {
    loading,
    data,
    page,
    pageCount,
    isLastPage,
    send: refresh,
    onSuccess,
  } = usePagination(
    (pageNo, size) =>
      alovaInstance.Get<PageResult<T>>(path, {
        params: { ...unref(params), pageNo, pageSize: size },
      }),
    {
      initialPage: 1,
      initialPageSize: pageSize,
      immediate,
      // alova 自动追加到列表（上拉加载更多模式）
      append: true,
      data: res => res.list,
      total: res => res.total,
    },
  )

  /** 加载更多（上拉触底调用） */
  function loadMore() {
    if (!isLastPage.value) {
      page.value++
    }
  }

  const list = computed(() => data.value ?? [])
  const total = computed(() => (data.value as unknown as { total?: number })?.total ?? 0)

  return {
    list,
    total,
    loading,
    page,
    pageCount,
    isLastPage,
    loadMore,
    refresh,
    onSuccess,
  }
}
