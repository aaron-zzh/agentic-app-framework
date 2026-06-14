/**
 * ScrollDownHint — 向下滚动提示，双箭头 + 呼吸光晕动画
 */
"use client"

import { motion } from "framer-motion"

interface ScrollDownHintProps {
  className?: string
}

export function ScrollDownHint({ className }: ScrollDownHintProps) {
  return (
    <motion.div
      className={`relative flex cursor-pointer flex-col items-center gap-0.5 ${className ?? ""}`}
      onClick={() => window.scrollBy({ top: window.innerHeight, behavior: "smooth" })}
      whileHover={{ scale: 1.15 }}
    >
      {/* 呼吸光晕 */}
      <motion.div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-10 w-10 rounded-full bg-white/15"
        animate={{ scale: [1, 1.9, 1], opacity: [0.4, 0, 0.4] }}
        transition={{ duration: 2, repeat: Infinity, ease: "easeInOut" }}
      />
      {/* 双箭头，错位淡入 */}
      {[0, 0.35].map((delay, i) => (
        <motion.svg
          key={i}
          width="20"
          height="12"
          viewBox="0 0 20 12"
          fill="none"
          className="text-white"
          animate={{ y: [0, 5, 0], opacity: [0.35, 1, 0.35] }}
          transition={{ duration: 1.5, repeat: Infinity, ease: "easeInOut", delay }}
        >
          <path
            d="M1 1l9 9 9-9"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </motion.svg>
      ))}
    </motion.div>
  )
}
