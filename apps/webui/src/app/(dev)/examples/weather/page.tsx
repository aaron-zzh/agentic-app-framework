"use client"

/**
 * 彩云天气示例页——实况 + 逐小时 + 7天预报
 * 路由：/examples/weather
 */

import { useEffect, useState } from "react"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { backendApi } from "@/lib/api/rest/backend-client"

// ─── 类型定义 ────────────────────────────────────────────────────────────────

interface WeatherResult {
  status: string
  /** 彩云返回坐标 [纬度, 经度]（注意顺序与请求参数相反） */
  location?: [number, number]
  result: {
    forecast_keypoint: string
    realtime: {
      temperature: number
      apparent_temperature: number
      humidity: number
      skycon: string
      wind: { speed: number; direction: number }
      precipitation: { local: { intensity: number } }
      air_quality: { aqi: { chn: number }; description: { chn: string } }
      life_index: {
        ultraviolet: { desc: string }
        comfort: { desc: string }
      }
    }
    hourly: {
      description: string
      temperature: { datetime: string; value: number }[]
      skycon: { datetime: string; value: string }[]
      precipitation: { datetime: string; value: number; probability: number }[]
      humidity: { datetime: string; value: number }[]
    }
    daily: {
      temperature: { date: string; max: number; min: number }[]
      skycon_08h_20h: { date: string; value: string }[]
      life_index: {
        dressing: { date: string; desc: string }[]
        comfort: { date: string; desc: string }[]
        ultraviolet: { date: string; desc: string }[]
      }
    }
  }
}

// ─── skycon 映射 ──────────────────────────────────────────────────────────────

const SKYCON_MAP: Record<string, { label: string; emoji: string }> = {
  CLEAR_DAY: { label: "晴天", emoji: "☀️" },
  CLEAR_NIGHT: { label: "晴夜", emoji: "🌙" },
  PARTLY_CLOUDY_DAY: { label: "多云", emoji: "⛅" },
  PARTLY_CLOUDY_NIGHT: { label: "多云夜", emoji: "🌤️" },
  CLOUDY: { label: "阴", emoji: "☁️" },
  LIGHT_HAZE: { label: "轻度霾", emoji: "🌫️" },
  MODERATE_HAZE: { label: "中度霾", emoji: "🌫️" },
  HEAVY_HAZE: { label: "重度霾", emoji: "🌫️" },
  LIGHT_RAIN: { label: "小雨", emoji: "🌦️" },
  MODERATE_RAIN: { label: "中雨", emoji: "🌧️" },
  HEAVY_RAIN: { label: "大雨", emoji: "🌧️" },
  STORM_RAIN: { label: "暴雨", emoji: "⛈️" },
  FOG: { label: "雾", emoji: "🌁" },
  LIGHT_SNOW: { label: "小雪", emoji: "🌨️" },
  MODERATE_SNOW: { label: "中雪", emoji: "❄️" },
  HEAVY_SNOW: { label: "大雪", emoji: "❄️" },
  STORM_SNOW: { label: "暴雪", emoji: "❄️" },
  DUST: { label: "浮尘", emoji: "🌪️" },
  SAND: { label: "沙尘", emoji: "🌪️" },
  WIND: { label: "大风", emoji: "💨" }
}

function skycon(code: string) {
  return SKYCON_MAP[code] ?? { label: code, emoji: "🌈" }
}

function fmtHour(datetime: string) {
  return datetime.slice(11, 16)
}

function fmtDate(date: string) {
  const d = new Date(date)
  const weekdays = ["日", "一", "二", "三", "四", "五", "六"]
  return `${d.getMonth() + 1}/${d.getDate()} 周${weekdays[d.getDay()]}`
}

// ─── 预设城市 ─────────────────────────────────────────────────────────────────

const PRESETS = [
  { label: "北京", lon: 116.3883, lat: 39.9289 },
  { label: "上海", lon: 121.4737, lat: 31.2304 },
  { label: "广州", lon: 113.2644, lat: 23.1291 },
  { label: "成都", lon: 104.0665, lat: 30.5723 },
  { label: "杭州", lon: 120.1536, lat: 30.2936 }
]

// ─── 主页面 ───────────────────────────────────────────────────────────────────

export default function WeatherPage() {
  const [lon, setLon] = useState("116.3883")
  const [lat, setLat] = useState("39.9289")
  const [ip, setIp] = useState("")
  const [loading, setLoading] = useState(false)
  const [locating, setLocating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [data, setData] = useState<WeatherResult | null>(null)
  const [cityName, setCityName] = useState<string | null>(null)

  async function query(longitude = lon, latitude = lat) {
    setLoading(true)
    setError(null)
    try {
      const json = await backendApi.get<WeatherResult>("/weather", {
        params: { longitude, latitude, dailysteps: 7, hourlysteps: 24 },
        showError: true
      })
      if (json.status !== "ok") throw new Error(json.status)
      setData(json)
    } catch (e) {
      setError(e instanceof Error ? e.message : "查询失败")
    } finally {
      setLoading(false)
    }
  }

  function locate() {
    if (!navigator.geolocation) {
      setError("浏览器不支持定位")
      return
    }
    setLocating(true)
    setError(null)
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const longitude = pos.coords.longitude.toFixed(4)
        const latitude = pos.coords.latitude.toFixed(4)
        setLon(longitude)
        setLat(latitude)
        setLocating(false)
        // 反查城市名
        try {
          const loc = await backendApi.get<{ admins?: { name: string }[] }>("/weather/location", {
            params: { longitude, latitude },
            showError: false
          })
          // admins: [{省}, {市}, {区}]，取最后一级
          const admins = loc.admins ?? []
          if (admins.length > 0) setCityName(admins[admins.length - 1].name)
        } catch {
          // 反查失败不影响天气查询
        }
        query(longitude, latitude)
      },
      (err) => {
        setLocating(false)
        setError(err.code === 1 ? "定位被拒绝，请在浏览器允许位置权限" : `定位失败：${err.message}`)
      },
      { timeout: 10000 }
    )
  }

  /**
   * 后端 IP 定位：调 /weather/by-ip，后端通过 ip2region + area 中心点拿到经纬度并查天气。
   * - 传 ipOverride（或输入框有值）→ 后端按指定 IP 定位（调试用）
   * - 不传 → 后端从请求头取真实 client IP
   * - silent=true 时失败不显示错误（用于进页面自动定位场景）
   *
   * 后端返回 { ip, location, longitude, latitude, weather }，一次拿全坐标 + 城市名 + 天气，
   * 无需再单独调 /weather/location 做逆地理编码。
   */
  async function locateByIp(silent = false, ipOverride?: string) {
    setLocating(true)
    if (!silent) setError(null)
    try {
      const targetIp = ipOverride ?? ip.trim()
      const res = await backendApi.get<{
        ip: string
        location: string | null
        longitude: number
        latitude: number
        weather: WeatherResult
      }>("/weather/by-ip", {
        params: targetIp ? { ip: targetIp } : undefined,
        showError: false
      })
      if (res.weather?.status !== "ok") throw new Error(res.weather?.status ?? "查询失败")
      setLon(String(res.longitude))
      setLat(String(res.latitude))
      if (res.location) setCityName(res.location)
      setData(res.weather)
    } catch (e) {
      if (!silent) setError(e instanceof Error ? e.message : "IP 定位失败")
      throw e
    } finally {
      setLocating(false)
    }
  }

  // 进页面自动 IP 定位，失败回退到默认坐标（北京）
  // biome-ignore lint/correctness/useExhaustiveDependencies: 仅挂载时执行一次
  useEffect(() => {
    locateByIp(true).catch(() => query())
  }, [])

  const rt = data?.result.realtime
  const hourly = data?.result.hourly
  const daily = data?.result.daily

  return (
    <div className="min-h-screen p-6">
      <div className="mx-auto max-w-4xl space-y-6">
        {/* 标题 */}
        <div>
          <h1 className="font-bold text-3xl">
            天气查询{cityName && <span className="ml-2 text-muted-foreground">· {cityName}</span>}
          </h1>
          <p className="mt-1 text-muted-foreground text-sm">
            基于彩云天气 v2.6 综合接口，返回实况 + 逐小时 + 7 天预报
          </p>
        </div>

        {/* 查询区域 */}
        <Card>
          <CardContent className="pt-6">
            <div className="flex flex-wrap items-end gap-3">
              <div className="space-y-1">
                <Label>经度</Label>
                <Input className="w-32" value={lon} onChange={(e) => setLon(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label>纬度</Label>
                <Input className="w-32" value={lat} onChange={(e) => setLat(e.target.value)} />
              </div>
              <div className="space-y-1">
                <Label>IP（可选）</Label>
                <Input
                  className="w-44"
                  placeholder="留空自动获取"
                  value={ip}
                  onChange={(e) => setIp(e.target.value)}
                />
              </div>
              <Button onClick={() => query()} disabled={loading}>
                {loading ? "查询中…" : "查询"}
              </Button>
              <Button variant="outline" onClick={locate} disabled={locating || loading}>
                {locating ? "定位中…" : "📍 当前位置"}
              </Button>
              <Button
                variant="outline"
                onClick={() => locateByIp(false)}
                disabled={locating || loading}
              >
                🌐 IP 定位
              </Button>
              <div className="flex gap-2">
                {PRESETS.map((p) => (
                  <Button
                    key={p.label}
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      setLon(String(p.lon))
                      setLat(String(p.lat))
                      query(String(p.lon), String(p.lat))
                    }}
                  >
                    {p.label}
                  </Button>
                ))}
              </div>
            </div>
            {error && <p className="mt-3 text-destructive text-sm">{error}</p>}
          </CardContent>
        </Card>

        {/* 实况 */}
        {rt && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <span className="text-2xl">{skycon(rt.skycon).emoji}</span>
                实况天气
                <span className="ml-auto font-normal text-muted-foreground text-sm">
                  {data?.result.forecast_keypoint}
                </span>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <Stat label="气温" value={`${rt.temperature}°C`} />
                <Stat label="体感温度" value={`${rt.apparent_temperature}°C`} />
                <Stat label="湿度" value={`${Math.round(rt.humidity * 100)}%`} />
                <Stat label="天气" value={skycon(rt.skycon).label} />
                <Stat label="风速" value={`${rt.wind.speed} km/h`} />
                <Stat label="降水强度" value={`${rt.precipitation.local.intensity} mm/h`} />
                <Stat
                  label="空气质量"
                  value={`AQI ${rt.air_quality.aqi.chn} ${rt.air_quality.description.chn}`}
                />
                <Stat label="舒适度" value={rt.life_index.comfort.desc} />
              </div>
            </CardContent>
          </Card>
        )}

        {/* 逐小时 */}
        {hourly && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">
                逐小时预报
                <span className="ml-2 font-normal text-muted-foreground text-sm">
                  {hourly.description}
                </span>
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="overflow-x-auto">
                <div className="flex gap-3 pb-2" style={{ minWidth: "max-content" }}>
                  {hourly.temperature.slice(0, 24).map((t, i) => {
                    const sky = hourly.skycon[i]
                    const precip = hourly.precipitation[i]
                    return (
                      <div
                        key={t.datetime}
                        className="flex flex-col items-center gap-1 rounded-lg bg-muted/50 p-2 text-center"
                        style={{ minWidth: "52px" }}
                      >
                        <span className="text-muted-foreground text-xs">{fmtHour(t.datetime)}</span>
                        <span className="text-lg">{sky ? skycon(sky.value).emoji : "—"}</span>
                        <span className="font-medium text-sm">{Math.round(t.value)}°</span>
                        {precip && precip.probability > 10 && (
                          <span className="text-blue-500 text-xs">{precip.probability}%</span>
                        )}
                      </div>
                    )
                  })}
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* 7天预报 */}
        {daily && (
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="text-base">7 天预报</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {daily.temperature.map((t, i) => {
                  const sky = daily.skycon_08h_20h[i]
                  const dress = daily.life_index.dressing[i]
                  const comfort = daily.life_index.comfort[i]
                  return (
                    <div
                      key={t.date}
                      className="flex items-center gap-4 rounded-lg px-3 py-2 hover:bg-muted/50"
                    >
                      <span className="w-24 text-muted-foreground text-sm">{fmtDate(t.date)}</span>
                      <span className="w-6 text-lg">{sky ? skycon(sky.value).emoji : "—"}</span>
                      <span className="w-16 text-sm">{sky ? skycon(sky.value).label : "—"}</span>
                      <span className="font-medium text-sm">
                        {Math.round(t.min)}° / {Math.round(t.max)}°
                      </span>
                      <span className="ml-auto text-muted-foreground text-xs">
                        {dress?.desc && `穿衣：${dress.desc}`}
                        {comfort?.desc && `　体感：${comfort.desc}`}
                      </span>
                    </div>
                  )
                })}
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </div>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg bg-muted/50 p-3">
      <p className="text-muted-foreground text-xs">{label}</p>
      <p className="mt-0.5 font-medium text-sm">{value}</p>
    </div>
  )
}
