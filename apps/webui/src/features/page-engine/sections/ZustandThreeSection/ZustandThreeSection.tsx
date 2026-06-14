"use client"

import { useEffect, useState } from "react"
import CodePreview from "./components/CodePreview"
import Counter from "./components/Counter"
import Details from "./components/Details"
import Scene from "./components/Scene"

const SECTION_ROOT_ID = "zustand-three-section"

export function ZustandThreeSection() {
  const [isDesktop, setIsDesktop] = useState(false)

  useEffect(() => {
    const mq = window.matchMedia("(min-width: 768px)")
    setIsDesktop(mq.matches)
    const handler = (e: MediaQueryListEvent) => setIsDesktop(e.matches)
    mq.addEventListener("change", handler)
    return () => mq.removeEventListener("change", handler)
  }, [])

  if (!isDesktop) return null

  return (
    <section
      id={SECTION_ROOT_ID}
      className="relative h-[720px] min-h-[640px] w-full overflow-hidden bg-[#010101] md:h-screen"
      style={{ WebkitTouchCallout: "none" }}
    >
      <Scene eventSourceId={SECTION_ROOT_ID} />
      <div className="absolute inset-0 text-white">
        <div className="absolute right-[10vw] mr-[-60px] flex h-full w-[640px] max-w-[80%] items-center justify-center max-md:mr-0">
          <div className="relative mb-[-60px]">
            <CodePreview />
            <Counter />
          </div>
        </div>
        <Details />
      </div>
    </section>
  )
}
