/**
 * 天气工具 API 与 TanStack Query Hook
 * @author AaronZZH & Kiro
 */

import { useQuery } from "@tanstack/react-query"

import { backendApi } from "../backend-client"

export interface WeatherVO {
  city: string
  description: string
  temperature: number
  humidity: number
  windDirection?: string
  windSpeed?: number
  forecast3Days?: string
  dataSource: string
  updatedAt: string
}

export const weatherApi = {
  get: (city: string): Promise<WeatherVO> =>
    backendApi.get(`/tools/weather?city=${encodeURIComponent(city)}`)
}

/** 查询城市天气（后端 30 分钟缓存） */
export function useWeather(city: string) {
  return useQuery({
    queryKey: ["tools", "weather", city] as const,
    queryFn: () => weatherApi.get(city),
    enabled: city.length > 0,
    staleTime: 30 * 60 * 1000
  })
}
