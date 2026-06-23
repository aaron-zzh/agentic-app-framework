/**
 * 工具-实时天气（彩云天气真实数据）
 * @author AaronZZH & Kiro
 */

"use client"

import { Cloud, MapPin } from "lucide-react"
import { useEffect, useState } from "react"
import { DataCapsule, GlassCard, GlassCardBody, GlowButton } from "@/components/studio"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { backendApi } from "@/lib/api/rest/backend-client"

// ─── 类型 ─────────────────────────────────────────────────────────────────────

interface WeatherResult {
  status: string
  result: {
    forecast_keypoint: string
    realtime: {
      temperature: number
      apparent_temperature: number
      humidity: number
      skycon: string
      wind: { speed: number; direction: number }
      air_quality: { aqi: { chn: number }; description: { chn: string } }
      life_index: { comfort: { desc: string } }
    }
    hourly: {
      description: string
      temperature: { datetime: string; value: number }[]
      skycon: { datetime: string; value: string }[]
      precipitation: { datetime: string; value: number; probability: number }[]
    }
    daily: {
      temperature: { date: string; max: number; min: number }[]
      skycon_08h_20h: { date: string; value: string }[]
    }
  }
}

const SKYCON_MAP: Record<string, { label: string; emoji: string }> = {
  CLEAR_DAY: { label: "晴", emoji: "☀️" },
  CLEAR_NIGHT: { label: "晴夜", emoji: "🌙" },
  PARTLY_CLOUDY_DAY: { label: "多云", emoji: "⛅" },
  PARTLY_CLOUDY_NIGHT: { label: "多云夜", emoji: "🌤️" },
  CLOUDY: { label: "阴", emoji: "☁️" },
  LIGHT_HAZE: { label: "轻霾", emoji: "🌫️" },
  MODERATE_HAZE: { label: "中霾", emoji: "🌫️" },
  HEAVY_HAZE: { label: "重霾", emoji: "🌫️" },
  LIGHT_RAIN: { label: "小雨", emoji: "🌦️" },
  MODERATE_RAIN: { label: "中雨", emoji: "🌧️" },
  HEAVY_RAIN: { label: "大雨", emoji: "🌧️" },
  STORM_RAIN: { label: "暴雨", emoji: "⛈️" },
  FOG: { label: "雾", emoji: "🌁" },
  LIGHT_SNOW: { label: "小雪", emoji: "🌨️" },
  MODERATE_SNOW: { label: "中雪", emoji: "❄️" },
  HEAVY_SNOW: { label: "大雪", emoji: "❄️" },
  WIND: { label: "大风", emoji: "💨" }
}

function skycon(code: string) {
  return SKYCON_MAP[code] ?? { label: code, emoji: "🌈" }
}

function fmtDate(date: string) {
  const d = new Date(date)
  const weekdays = ["日", "一", "二", "三", "四", "五", "六"]
  return `${d.getMonth() + 1}/${d.getDate()} 周${weekdays[d.getDay()]}`
}

function fmtHour(datetime: string) {
  return datetime.slice(11, 16)
}

const PRESETS = [
  { label: "北京", lon: 116.3883, lat: 39.9289 },
  { label: "上海", lon: 121.4737, lat: 31.2304 },
  { label: "广州", lon: 113.2644, lat: 23.1291 },
  { label: "成都", lon: 104.0665, lat: 30.5723 },
  { label: "杭州", lon: 120.1536, lat: 30.2936 }
]

// ─── 主页面 ───────────────────────────────────────────────────────────────────

export default function WeatherToolPage() {
  const [loading, setLoading] = useState(false)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [data, setData] = useState<WeatherResult | null>(null)
  const [cityName, setCityName] = useState<string | null>(null)

  async function query(longitude: string, latitude: string) {
    setLoading(true)
    setError(null)
    try {
      const json = await backendApi.get<WeatherResult>("/weather", {
        params: { longitude, latitude, dailysteps: 5, hourlysteps: 24 },
        showError: false
      })
      if (json.status !== "ok") throw new Error("查询失败")
      setData(json)
    } catch {
      setError("天气查询失败，请稍后重试")
    } finally {
      setLoading(false)
    }
  }

  async function locateByIp() {
    setLocating(true)
    setError(null)
    try {
      const res = await backendApi.get<{
        location: string | null
        longitude: number
        latitude: number
        weather: WeatherResult
      }>("/weather/by-ip", { showError: false })
      if (res.weather?.status !== "ok") throw new Error()
      if (res.location) setCityName(res.location)
      setData(res.weather)
    } catch {
      query("116.3883", "39.9289") // 失败回退北京
    } finally {
      setLocating(false)
    }
  }

  // 进页面自动 IP 定位
  // biome-ignore lint/correctness/useExhaustiveDependencies: 仅挂载时执行一次
  useEffect(() => {
    locateByIp()
  }, [])

  const rt = data?.result.realtime
  const hourly = data?.result.hourly
  const daily = data?.result.daily

  return (
    <div className="mx-auto max-w-6xl space-y-6 p-6">
      <header className="flex items-center gap-3">
        <Cloud className="size-5 text-sky-400" />
        <h1 className="font-semibold text-xl">
          实时天气
          {cityName && (
            <span className="ml-2 font-normal text-base text-muted-foreground">· {cityName}</span>
          )}
        </h1>
        <Button
          variant="outline"
          size="sm"
          className="ml-auto gap-1.5"
          onClick={locateByIp}
          disabled={locating}
        >
          <MapPin className="size-3.5" />
          {locating ? "定位中..." : "自动定位"}
        </Button>
      </header>

      {/* 预设城市 */}
      <div className="flex flex-wrap gap-2">
        {PRESETS.map((p) => (
          <GlowButton
            key={p.label}
            tone="ghost"
            size="sm"
            onClick={() => {
              setCityName(p.label)
              query(String(p.lon), String(p.lat))
            }}
          >
            {p.label}
          </GlowButton>
        ))}
      </div>

      {error && <p className="text-destructive text-sm">{error}</p>}

      {loading || locating ? (
        <div className="space-y-3">
          <Skeleton className="h-32 rounded-2xl" />
          <Skeleton className="h-24 rounded-2xl" />
        </div>
      ) : rt ? (
        <>
          {/* 实况 */}
          <GlassCard glow="cyan">
            <GlassCardBody>
              <div className="mb-4 flex items-center gap-4">
                <span className="text-6xl">{skycon(rt.skycon).emoji}</span>
                <div>
                  <p className="font-bold text-5xl">{rt.temperature}°C</p>
                  <p className="text-muted-foreground">
                    {skycon(rt.skycon).label} · {data?.result.forecast_keypoint}
                  </p>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                <DataCapsule label="体感" value={`${rt.apparent_temperature}°C`} tone="cyan" />
                <DataCapsule
                  label="湿度"
                  value={`${Math.round(rt.humidity * 100)}%`}
                  tone="violet"
                />
                <DataCapsule label="风速" value={`${rt.wind.speed} km/h`} tone="default" />
                <DataCapsule label="空气" value={rt.air_quality.description.chn} tone="emerald" />
              </div>
            </GlassCardBody>
          </GlassCard>

          {/* 逐小时 */}
          {hourly && (
            <GlassCard glow="none">
              <GlassCardBody>
                <p className="mb-3 font-medium text-sm">
                  逐小时{" "}
                  <span className="font-normal text-muted-foreground">{hourly.description}</span>
                </p>
                <div className="overflow-x-auto">
                  <div className="flex gap-2 pb-1" style={{ minWidth: "max-content" }}>
                    {hourly.temperature.slice(0, 24).map((t, i) => {
                      const sky = hourly.skycon[i]
                      const precip = hourly.precipitation[i]
                      return (
                        <div
                          key={t.datetime}
                          className="flex min-w-[52px] flex-col items-center gap-1 rounded-xl bg-foreground/[0.04] px-3 py-2 text-center"
                        >
                          <span className="text-muted-foreground text-xs">
                            {fmtHour(t.datetime)}
                          </span>
                          <span className="text-xl">{sky ? skycon(sky.value).emoji : "—"}</span>
                          <span className="font-medium text-sm">{Math.round(t.value)}°</span>
                          {precip && precip.probability > 10 && (
                            <span className="text-[10px] text-sky-400">{precip.probability}%</span>
                          )}
                        </div>
                      )
                    })}
                  </div>
                </div>
              </GlassCardBody>
            </GlassCard>
          )}

          {/* 5天预报 */}
          {daily && (
            <GlassCard glow="none">
              <GlassCardBody className="space-y-2">
                <p className="mb-3 font-medium text-sm">未来预报</p>
                {daily.temperature.map((t, i) => {
                  const sky = daily.skycon_08h_20h[i]
                  return (
                    <div
                      key={t.date}
                      className="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-foreground/[0.03]"
                    >
                      <span className="w-24 text-muted-foreground text-xs">{fmtDate(t.date)}</span>
                      <span className="text-lg">{sky ? skycon(sky.value).emoji : "—"}</span>
                      <span className="text-muted-foreground text-sm">
                        {sky ? skycon(sky.value).label : "—"}
                      </span>
                      <span className="ml-auto font-medium text-sm">
                        {Math.round(t.min)}° / {Math.round(t.max)}°
                      </span>
                    </div>
                  )
                })}
              </GlassCardBody>
            </GlassCard>
          )}
        </>
      ) : null}
    </div>
  )
}
