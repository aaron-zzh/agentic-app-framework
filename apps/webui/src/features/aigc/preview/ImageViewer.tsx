/**
 * 图像查看器——支持滚轮缩放、左键拖动、双击重置
 */

"use client"

import { useCallback, useEffect, useRef, useState } from "react"

interface ViewState {
  scale: number
  offsetX: number
  offsetY: number
}

interface Props {
  src: string
  alt?: string
  className?: string
  onLoad?: (e: React.SyntheticEvent<HTMLImageElement>) => void
}

const MIN_SCALE = 0.1
const MAX_SCALE = 20

export function ImageViewer({ src, alt = "", className, onLoad }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const imgRef = useRef<HTMLImageElement>(null)
  const panStart = useRef<{ x: number; y: number; ox: number; oy: number } | null>(null)

  const [view, setView] = useState<ViewState>({ scale: 1, offsetX: 0, offsetY: 0 })
  const [isDragging, setIsDragging] = useState(false)
  const [loaded, setLoaded] = useState(false)

  const fitView = useCallback(() => {
    const el = containerRef.current
    const img = imgRef.current
    if (!el || !img?.naturalWidth) return
    const scale = Math.min(el.clientWidth / img.naturalWidth, el.clientHeight / img.naturalHeight)
    const offsetX = (el.clientWidth - img.naturalWidth * scale) / 2
    const offsetY = (el.clientHeight - img.naturalHeight * scale) / 2
    setView({ scale, offsetX, offsetY })
  }, [])

  // src 变化时重置视图和加载状态
  useEffect(() => {
    setView({ scale: 1, offsetX: 0, offsetY: 0 })
    setLoaded(false)
  }, [src])

  // 滚轮缩放（以鼠标为中心）
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      const rect = el.getBoundingClientRect()
      const mx = e.clientX - rect.left
      const my = e.clientY - rect.top
      setView((prev) => {
        const factor = e.deltaY < 0 ? 1.15 : 1 / 1.15
        const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, prev.scale * factor))
        const r = newScale / prev.scale
        return {
          scale: newScale,
          offsetX: mx - r * (mx - prev.offsetX),
          offsetY: my - r * (my - prev.offsetY)
        }
      })
    }
    el.addEventListener("wheel", onWheel, { passive: false })
    return () => el.removeEventListener("wheel", onWheel)
  }, [])

  const getPos = useCallback((e: React.MouseEvent) => {
    const rect = containerRef.current?.getBoundingClientRect()
    if (!rect) return { x: 0, y: 0 }
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }, [])

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (e.button !== 0) return
      const pos = getPos(e)
      panStart.current = { x: pos.x, y: pos.y, ox: view.offsetX, oy: view.offsetY }
      setIsDragging(false)
    },
    [getPos, view.offsetX, view.offsetY]
  )

  const handleMouseMove = useCallback(
    (e: React.MouseEvent) => {
      if (!panStart.current) return
      const start = panStart.current
      const pos = getPos(e)
      const dx = pos.x - start.x
      const dy = pos.y - start.y
      if (Math.abs(dx) > 3 || Math.abs(dy) > 3) setIsDragging(true)
      setView((prev) => ({
        ...prev,
        offsetX: start.ox + dx,
        offsetY: start.oy + dy
      }))
    },
    [getPos]
  )

  const handleMouseUp = useCallback(() => {
    panStart.current = null
    setIsDragging(false)
  }, [])

  const handleDoubleClick = useCallback(() => {
    fitView()
  }, [fitView])

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label={alt || "图像查看器"}
      className={`group relative overflow-hidden rounded-[8px] ${className ?? ""}`}
      style={{ cursor: isDragging ? "grabbing" : "grab", userSelect: "none" }}
      onMouseDown={handleMouseDown}
      onMouseMove={handleMouseMove}
      onMouseUp={handleMouseUp}
      onMouseLeave={handleMouseUp}
      onDoubleClick={handleDoubleClick}
    >
      {/* biome-ignore lint/performance/noImgElement: 动态预览大图，需要 transform 定位 */}
      <img
        ref={imgRef}
        src={src}
        alt={alt}
        draggable={false}
        className="absolute top-0 left-0 max-w-none rounded-lg transition-opacity duration-200"
        style={{
          transformOrigin: "0 0",
          transform: `translate(${view.offsetX}px, ${view.offsetY}px) scale(${view.scale})`,
          pointerEvents: "none",
          opacity: loaded ? 1 : 0
        }}
        onLoad={(e) => {
          fitView()
          setLoaded(true)
          onLoad?.(e)
        }}
      />
      <span className="pointer-events-none absolute right-2 bottom-2 rounded bg-black/40 px-1.5 py-0.5 text-[10px] text-white/50 opacity-0 backdrop-blur-sm transition-opacity group-hover:opacity-100">
        滚轮缩放 · 拖动平移 · 双击适配
      </span>
    </div>
  )
}
