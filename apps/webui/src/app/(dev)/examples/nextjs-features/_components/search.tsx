/**
 * 特性演示：URL 搜索参数 + 防抖
 *
 * 核心模式：
 * 1. useSearchParams()：读取当前 URL 参数（仅客户端组件可用）
 * 2. useDebouncedCallback：300ms 防抖，避免每次击键都触发路由变更
 * 3. router.replace()：修改 URL 但不新增历史记录，URL 即搜索状态
 * 4. defaultValue 而非 value：受控于 URL，不需要额外 useState
 */
"use client"

import { Search as MagnifyingGlassIcon } from "lucide-react"
import { usePathname, useRouter, useSearchParams } from "next/navigation"
import { useDebouncedCallback } from "use-debounce"

export default function Search({ placeholder }: { placeholder: string }) {
  const searchParams = useSearchParams()
  const pathname = usePathname()
  const { replace } = useRouter()

  const handleSearch = useDebouncedCallback((term: string) => {
    const params = new URLSearchParams(searchParams)
    params.set("page", "1") // 搜索时重置到第一页
    if (term) {
      params.set("query", term)
    } else {
      params.delete("query")
    }
    replace(`${pathname}?${params.toString()}`)
  }, 300)

  return (
    <div className="relative flex flex-1 shrink-0">
      <label htmlFor="search" className="sr-only">
        Search
      </label>
      <input
        id="search"
        className="peer block w-full rounded-md border border-gray-200 py-2 pl-10 text-sm outline-2 placeholder:text-gray-500"
        placeholder={placeholder}
        onChange={(e) => handleSearch(e.target.value)}
        defaultValue={searchParams.get("query") ?? ""}
      />
      <MagnifyingGlassIcon className="absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-gray-500 peer-focus:text-gray-900" />
    </div>
  )
}
