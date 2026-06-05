"use client"

import { OrbitControls, View as ViewImpl } from "@react-three/drei"
import { useImperativeHandle, useRef } from "react"
import { ToThree } from "./Three"

interface ViewProps extends React.HTMLAttributes<HTMLDivElement> {
  children?: React.ReactNode
  orbit?: boolean
  ref?: React.Ref<HTMLDivElement>
}

// View 子元素会渲染到 Scene 中的 Canvas
export const View = ({ children, orbit, ref, ...props }: ViewProps) => {
  const localRef = useRef<HTMLDivElement>(null)
  useImperativeHandle(ref, () => localRef.current as HTMLDivElement)

  return (
    <>
      <div ref={localRef} {...props} />
      <ToThree>
        {/* @ts-ignore */}
        <ViewImpl track={localRef}>
          {children}
          {orbit && <OrbitControls />}
        </ViewImpl>
      </ToThree>
    </>
  )
}
View.displayName = "3DView"
