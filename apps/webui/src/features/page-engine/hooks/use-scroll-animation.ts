/**
 * useScrollAnimation——Intersection Observer 滚动动效 hook
 * 当元素进入视口时触发 CSS 动画类名注入
 * @author AaronZZH & Kiro
 *
 * @example
 * ```tsx
 * const ref = useScrollAnimation<HTMLDivElement>("fadeIn")
 * return <div ref={ref}>内容</div>
 * ```
 */

"use client"

import { useEffect, useRef } from "react"

/** 支持的动画类型 */
export type AnimationType = "fadeIn" | "slideUp" | "slideLeft" | "slideRight" | "scaleIn" | "none"

/** 动画类型到 CSS 类名映射 */
const animationClassMap: Record<AnimationType, string> = {
  fadeIn: "animate-in fade-in duration-700",
  slideUp: "animate-in fade-in slide-in-from-bottom-8 duration-700",
  slideLeft: "animate-in fade-in slide-in-from-left-8 duration-700",
  slideRight: "animate-in fade-in slide-in-from-right-8 duration-700",
  scaleIn: "animate-in fade-in zoom-in-95 duration-500",
  none: ""
}

interface ScrollAnimationOptions {
  /** 触发阈值（0-1） */
  threshold?: number
  /** 是否只触发一次 */
  once?: boolean
  /** 延迟（ms） */
  delay?: number
}

/**
 * 滚动动效 hook——元素进入视口时添加动画类名
 */
export function useScrollAnimation<T extends HTMLElement = HTMLDivElement>(
  animation: AnimationType = "fadeIn",
  options: ScrollAnimationOptions = {}
): React.RefObject<T | null> {
  const ref = useRef<T | null>(null)
  const { threshold = 0.1, once = true, delay = 0 } = options

  useEffect(() => {
    const el = ref.current
    if (!el || animation === "none") return

    // 初始隐藏
    el.style.opacity = "0"

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          const apply = () => {
            el.style.opacity = ""
            const classes = animationClassMap[animation].split(" ").filter(Boolean)
            el.classList.add(...classes)
          }
          if (delay > 0) {
            setTimeout(apply, delay)
          } else {
            apply()
          }
          if (once) observer.unobserve(el)
        }
      },
      { threshold }
    )

    observer.observe(el)
    return () => observer.disconnect()
  }, [animation, threshold, once, delay])

  return ref
}
