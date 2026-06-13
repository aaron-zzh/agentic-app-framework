/**
 * @提及用户搜索 hook
 * @author AaronZZH & Kiro
 */

import { useEffect, useState } from "react"
import { backendApi } from "@/lib/api/rest/backend-client"

export interface MentionUser {
  id: number
  username: string
  nickname: string
}

/** 搜索可提及用户（基于 /users/simple 过滤） */
export function useMentionSearch(query: string | null) {
  const [users, setUsers] = useState<MentionUser[]>([])

  useEffect(() => {
    if (query === null) {
      setUsers([])
      return
    }
    backendApi
      .get<MentionUser[]>("/users/simple")
      .then((list) => {
        const q = query.toLowerCase()
        setUsers(
          q
            ? list.filter(
                (u) => u.nickname?.toLowerCase().includes(q) || u.username.toLowerCase().includes(q)
              )
            : list.slice(0, 8)
        )
      })
      .catch(() => setUsers([]))
  }, [query])

  return users
}
