import { QueryCache, QueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { ApiError } from "../errors"

export function createQueryClient(): QueryClient {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (error: unknown) => {
        if (error instanceof ApiError && error.code === 403) {
          toast.error("权限不足，无法访问该资源")
          if (typeof window !== "undefined") {
            window.location.href = "/studio"
          }
        }
      }
    }),
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        gcTime: 10 * 60 * 1000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.code === 401) return false
          if (error instanceof ApiError && error.code === 403) return false
          if (error instanceof ApiError && error.code === 0) return false
          return failureCount < 3
        }
      },
      mutations: {
        retry: 0
      }
    }
  })
}
