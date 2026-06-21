"use client"

import { useEffect, useId, useRef } from "react"

const SVG_NS = "http://www.w3.org/2000/svg"

// Rose Curve: r = a·cos(k·t)，五瓣玫瑰线粒子尾迹加载动画
// 参数参考 paidax01.github.io/math-curve-loaders/
export interface RoseCurveLoaderProps {
  size?: number
  /** 纯色模式下的粒子颜色，支持 CSS 颜色值或 currentColor */
  color?: string
  /** 开启渐变光效模式，指定起止色（默认紫→青） */
  gradient?: { from: string; to: string } | true
  /** 开启 glow 光晕滤镜（gradient 模式下效果更佳） */
  glow?: boolean
  className?: string
}

const CONFIG = {
  particleCount: 78,
  trailSpan: 0.32,
  durationMs: 5400,
  pulseDurationMs: 4600,
  rotationDurationMs: 28000,
  strokeWidth: 4.5,
  roseA: 9.2,
  roseABoost: 0.6,
  roseBreathBase: 0.72,
  roseBreathBoost: 0.28,
  roseK: 5,
  roseScale: 3.25
}

const DEFAULT_GRADIENT = { from: "#a78bfa", to: "#22d3ee" }

function getRosePoint(progress: number, detailScale: number): { x: number; y: number } {
  const t = progress * Math.PI * 2
  const a = CONFIG.roseA + detailScale * CONFIG.roseABoost
  const r =
    a * (CONFIG.roseBreathBase + detailScale * CONFIG.roseBreathBoost) * Math.cos(CONFIG.roseK * t)
  return { x: 50 + Math.cos(t) * r * CONFIG.roseScale, y: 50 + Math.sin(t) * r * CONFIG.roseScale }
}

function normalizeProgress(p: number): number {
  return ((p % 1) + 1) % 1
}

function getDetailScale(time: number, phaseOffset: number): number {
  const p =
    ((time + phaseOffset * CONFIG.pulseDurationMs) % CONFIG.pulseDurationMs) /
    CONFIG.pulseDurationMs
  return 0.52 + ((Math.sin(p * Math.PI * 2 + 0.55) + 1) / 2) * 0.48
}

function buildPath(detailScale: number): string {
  const steps = 480
  return Array.from({ length: steps + 1 }, (_, i) => {
    const p = getRosePoint(i / steps, detailScale)
    return `${i === 0 ? "M" : "L"} ${p.x.toFixed(2)} ${p.y.toFixed(2)}`
  }).join(" ")
}

// 通过 lerp 在 from/to 之间插值，给尾迹粒子着色
function lerpColor(from: string, to: string, t: number): string {
  // 仅支持 hex 格式，其他格式降级为 from
  const parse = (hex: string) => {
    const m = /^#([0-9a-f]{3,6})$/i.exec(hex.trim())
    if (!m) return null
    const h = m[1].length === 3 ? m[1].replace(/./g, (c) => c + c) : m[1]
    return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)]
  }
  const f = parse(from)
  const tv = parse(to)
  if (!f || !tv) return from
  const r = Math.round(f[0] + (tv[0] - f[0]) * t)
  const g = Math.round(f[1] + (tv[1] - f[1]) * t)
  const b = Math.round(f[2] + (tv[2] - f[2]) * t)
  return `rgb(${r},${g},${b})`
}

export function RoseCurveLoader({
  size = 80,
  color = "currentColor",
  gradient,
  glow = false,
  className
}: RoseCurveLoaderProps) {
  const id = useId().replace(/:/g, "")
  const filterId = `rose-glow-${id}`
  const gradId = `rose-grad-${id}`

  const groupRef = useRef<SVGGElement>(null)
  const pathRef = useRef<SVGPathElement>(null)
  const rafRef = useRef<number>(0)
  const startTimeRef = useRef<number>(performance.now())
  const phaseOffset = useRef<number>(Math.random())

  const gradColors = gradient === true ? DEFAULT_GRADIENT : (gradient ?? null)

  useEffect(() => {
    const group = groupRef.current
    const path = pathRef.current
    if (!group || !path) return

    const particles: SVGCircleElement[] = Array.from({ length: CONFIG.particleCount }, () => {
      const c = document.createElementNS(SVG_NS, "circle")
      if (!gradColors) c.setAttribute("fill", color)
      group.appendChild(c)
      return c
    })

    function tick(now: number) {
      const time = now - startTimeRef.current
      const progress =
        ((time + phaseOffset.current * CONFIG.durationMs) % CONFIG.durationMs) / CONFIG.durationMs
      const detailScale = getDetailScale(time, phaseOffset.current)
      const rotation =
        -(
          ((time + phaseOffset.current * CONFIG.rotationDurationMs) % CONFIG.rotationDurationMs) /
          CONFIG.rotationDurationMs
        ) * 360

      group?.setAttribute("transform", `rotate(${rotation} 50 50)`)
      path?.setAttribute("d", buildPath(detailScale))

      particles.forEach((node, index) => {
        const tailOffset = index / (CONFIG.particleCount - 1)
        const pt = getRosePoint(
          normalizeProgress(progress - tailOffset * CONFIG.trailSpan),
          detailScale
        )
        const fade = (1 - tailOffset) ** 0.56
        node.setAttribute("cx", pt.x.toFixed(2))
        node.setAttribute("cy", pt.y.toFixed(2))
        node.setAttribute("r", (0.9 + fade * 2.7).toFixed(2))
        node.setAttribute("opacity", (0.04 + fade * 0.96).toFixed(3))
        // 渐变模式：头部亮色→尾部暗色
        if (gradColors) {
          node.setAttribute("fill", lerpColor(gradColors.from, gradColors.to, tailOffset))
        }
      })

      rafRef.current = requestAnimationFrame(tick)
    }

    rafRef.current = requestAnimationFrame(tick)
    return () => {
      cancelAnimationFrame(rafRef.current)
      particles.forEach((c) => {
        c.remove()
      })
    }
  }, [color, gradColors])

  const strokeColor = gradColors ? `url(#${gradId})` : color

  return (
    <svg
      xmlns={SVG_NS}
      viewBox="0 0 100 100"
      fill="none"
      width={size}
      height={size}
      className={className}
      aria-label="加载中"
      role="img"
    >
      <defs>
        {/* 轨迹描边渐变 */}
        {gradColors && (
          <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor={gradColors.from} />
            <stop offset="100%" stopColor={gradColors.to} />
          </linearGradient>
        )}
        {/* glow 滤镜：feGaussianBlur 模糊后叠加原图 */}
        {glow && (
          <filter id={filterId} x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="1.8" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        )}
      </defs>
      <g ref={groupRef} filter={glow ? `url(#${filterId})` : undefined}>
        <path
          ref={pathRef}
          stroke={strokeColor}
          strokeWidth={CONFIG.strokeWidth}
          strokeLinecap="round"
          strokeLinejoin="round"
          opacity={0.12}
        />
      </g>
    </svg>
  )
}
