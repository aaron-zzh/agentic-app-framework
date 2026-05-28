/**
 * 3D 基础场景容器——Canvas + 灯光 + OrbitControls + Grid
 * @author AaronZZH & Kiro
 */

"use client"

import { AdaptiveDpr, GizmoHelper, GizmoViewport, Grid, OrbitControls } from "@react-three/drei"
import { Canvas } from "@react-three/fiber"
import { cn } from "@/lib/utils/index"

interface ThreeSceneProps {
  className?: string
  children?: React.ReactNode
}

/** 场景内容（灯光 + 辅助线 + 子节点） */
function SceneContent({ children }: { children?: React.ReactNode }) {
  return (
    <>
      {/* 环境光 + 方向光 */}
      <ambientLight intensity={0.5} />
      <directionalLight position={[5, 10, 5]} intensity={1} castShadow />

      {/* 网格地面 */}
      <Grid
        args={[20, 20]}
        cellSize={1}
        cellThickness={0.5}
        cellColor="#6b7280"
        sectionSize={5}
        sectionThickness={1}
        sectionColor="#374151"
        fadeDistance={30}
        infiniteGrid
      />

      {/* 坐标轴辅助线 */}
      <axesHelper args={[5]} />

      {/* 视角辅助器 */}
      <GizmoHelper alignment="bottom-right" margin={[60, 60]}>
        <GizmoViewport />
      </GizmoHelper>

      {/* 轨道控制器 */}
      <OrbitControls makeDefault enableDamping dampingFactor={0.1} />

      {/* 自适应分辨率 */}
      <AdaptiveDpr pixelated />

      {/* 用户放置的模型 */}
      {children}
    </>
  )
}

export function ThreeScene({ className, children }: ThreeSceneProps) {
  return (
    <div className={cn("size-full", className)}>
      <Canvas
        frameloop="demand"
        camera={{ position: [5, 5, 5], fov: 50 }}
        gl={{ preserveDrawingBuffer: true }}
      >
        <SceneContent>{children}</SceneContent>
      </Canvas>
    </div>
  )
}
