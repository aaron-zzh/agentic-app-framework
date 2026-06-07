/**
 * LottieIcon——基于 lottie-web 的轻量动画组件
 * 支持 animationData 对象或 name（/icons/lottie/{name}.json 路径）两种数据源
 * @author Kiro
 */

"use client"

import type { AnimationItem } from "lottie-web"
import { useCallback, useEffect, useRef } from "react"

export interface LottieIconProps {
  /** 内联 JSON 动画数据（与 name 二选一） */
  animationData?: object
  /** 动画文件名，加载 /icons/lottie/{name}.json（与 animationData 二选一） */
  name?: string
  width?: number | string
  height?: number | string
  className?: string
  loop?: boolean
  autoplay?: boolean
  /** 渲染器，默认 svg */
  renderer?: "svg" | "canvas" | "html"
  /** 鼠标悬停时才播放 */
  playOnHover?: boolean
  onComplete?: () => void
  onLoopComplete?: () => void
}

export function LottieIcon({
  animationData,
  name,
  width = 300,
  height = 300,
  className,
  loop = true,
  autoplay = true,
  renderer = "svg",
  playOnHover = false,
  onComplete,
  onLoopComplete
}: LottieIconProps) {
  const containerRef = useRef<HTMLButtonElement>(null)
  const animationRef = useRef<AnimationItem | null>(null)

  const stableOnComplete = useCallback(() => onComplete?.(), [onComplete])
  const stableOnLoopComplete = useCallback(() => onLoopComplete?.(), [onLoopComplete])

  useEffect(() => {
    if (!containerRef.current) return

    const path = name ? `/icons/lottie/${name}.json` : undefined

    import("lottie-web").then(({ default: lottie }) => {
      if (!containerRef.current) return
      animationRef.current = lottie.loadAnimation({
        container: containerRef.current,
        renderer,
        loop: playOnHover ? false : loop,
        autoplay: playOnHover ? false : autoplay,
        ...(animationData ? { animationData } : { path })
      })

      const anim = animationRef.current

      if (onComplete) anim.addEventListener("complete", stableOnComplete)
      if (onLoopComplete) anim.addEventListener("loopComplete", stableOnLoopComplete)
    })

    return () => {
      const anim = animationRef.current
      if (anim) {
        if (onComplete) anim.removeEventListener("complete", stableOnComplete)
        if (onLoopComplete) anim.removeEventListener("loopComplete", stableOnLoopComplete)
        anim.destroy()
        animationRef.current = null
      }
    }
  }, [
    animationData,
    name,
    loop,
    autoplay,
    playOnHover,
    renderer,
    onComplete,
    onLoopComplete,
    stableOnComplete,
    stableOnLoopComplete
  ])

  const handleMouseEnter = () => {
    if (playOnHover && animationRef.current) {
      animationRef.current.goToAndStop(0, true)
      animationRef.current.play()
    }
  }

  const handleMouseLeave = () => {
    if (playOnHover && animationRef.current) {
      animationRef.current.goToAndStop(0, true)
    }
  }

  return (
    <button
      ref={containerRef}
      type="button"
      aria-label={playOnHover ? "animation" : undefined}
      className={className}
      style={{
        width,
        height,
        background: "none",
        border: "none",
        padding: 0,
        cursor: playOnHover ? "pointer" : "default"
      }}
      onMouseEnter={playOnHover ? handleMouseEnter : undefined}
      onMouseLeave={playOnHover ? handleMouseLeave : undefined}
    />
  )
}
