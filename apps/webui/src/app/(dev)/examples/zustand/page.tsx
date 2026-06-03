"use client"

import CodePreview from "./_components/CodePreview"
import Counter from "./_components/Counter"
import Details from "./_components/Details"
import Scene from "./_components/Scene"

export default function App() {
  return (
    <div
      id="root"
      className="m-0 h-screen w-full overflow-hidden bg-[#010101] p-0"
      style={{ WebkitTouchCallout: "none" }}
    >
      <Scene />
      <div className="absolute inset-0 text-white">
        <div className="absolute right-[10vw] mr-[-60px] flex h-full w-[640px] max-w-[80%] items-center justify-center max-md:mr-0">
          <div className="relative mb-[-60px]">
            <CodePreview />
            <Counter />
          </div>
        </div>
        <Details />
      </div>
    </div>
  )
}
