import { QueryClient } from "@tanstack/react-query"
import { ApiError } from "../errors"

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        gcTime: 10 * 60 * 1000,
        refetchOnWindowFocus: false,
        refetchOnReconnect: true,
        retry: (failureCount, error) => {
          if (error instanceof ApiError && error.code === 401) return false
          return failureCount < 3
        }
      },
      mutations: {
        retry: 1
      }
    }
  })
}
