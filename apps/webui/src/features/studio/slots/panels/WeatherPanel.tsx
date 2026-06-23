/**
 * 天气面板（演示）——复用 useWeather hook
 * payload.city: 默认城市，未传则用上次/北京
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { useWeather } from "@/lib/queries/use-weather"
import type { SlotPanelProps } from "../registry"

export function WeatherPanel({ payload }: SlotPanelProps) {
  const initialCity = (payload?.city as string) || "北京"
  const [city, setCity] = useState(initialCity)
  const { data: weather, isLoading } = useWeather(city)

  return (
    <div className="space-y-3">
      <Input
        placeholder="城市"
        value={city}
        onChange={(e) => setCity(e.target.value)}
        className="h-7 bg-foreground/[0.02] text-xs"
      />
      {isLoading ? (
        <div className="space-y-2">
          <Skeleton className="h-8 w-1/2" />
          <Skeleton className="h-3 w-full" />
        </div>
      ) : weather ? (
        <div className="space-y-2">
          <div className="flex items-end gap-2">
            <span className="font-bold text-3xl">{weather.temperature}°</span>
            <span className="mb-1 text-muted-foreground text-xs">{weather.description}</span>
          </div>
          <div className="grid grid-cols-2 gap-1.5 text-xs">
            <div className="rounded bg-foreground/[0.04] px-2 py-1">
              <span className="text-muted-foreground">湿度</span>
              <span className="ml-1">{weather.humidity}%</span>
            </div>
            {weather.windSpeed !== undefined && (
              <div className="rounded bg-foreground/[0.04] px-2 py-1">
                <span className="text-muted-foreground">风速</span>
                <span className="ml-1">{weather.windSpeed} km/h</span>
              </div>
            )}
          </div>
          {weather.forecast3Days && (
            <p className="text-muted-foreground text-xs leading-relaxed">{weather.forecast3Days}</p>
          )}
        </div>
      ) : (
        <p className="text-muted-foreground text-xs">输入城市查询</p>
      )}
    </div>
  )
}
