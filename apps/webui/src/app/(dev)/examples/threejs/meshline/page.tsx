"use client"

import { Canvas, extend, useFrame } from "@react-three/fiber"
import { Bloom, EffectComposer } from "@react-three/postprocessing"
import { useControls } from "leva"
import { easing } from "maath"
import { MeshLineGeometry, MeshLineMaterial } from "meshline"
import { useMemo, useRef } from "react"
import * as THREE from "three"

extend({ MeshLineGeometry, MeshLineMaterial })

// 注册自定义 r3f 元素类型
declare module "@react-three/fiber" {
  interface ThreeElements {
    meshLineGeometry: { points: number[] } & React.JSX.IntrinsicElements["mesh"]
    meshLineMaterial: {
      transparent?: boolean
      lineWidth?: number
      color?: string | number[]
      depthWrite?: boolean
      dashArray?: number
      dashRatio?: number
      toneMapped?: boolean
      dashOffset?: number
    } & React.JSX.IntrinsicElements["mesh"]
  }
}

import type * as React from "react"

export default function App() {
  const { dash, count, radius } = useControls({
    dash: { value: 0.9, min: 0, max: 0.99, step: 0.01 },
    count: { value: 50, min: 0, max: 200, step: 1 },
    radius: { value: 50, min: 1, max: 100, step: 1 }
  })
  return (
    <div className="h-screen w-screen">
      <Canvas camera={{ position: [0, 0, 5], fov: 90 }}>
        <color attach="background" args={["#101020"]} />
        <Lines
          dash={dash}
          count={count}
          radius={radius}
          colors={[[10, 0.5, 2], [1, 2, 10], "#A2CCB6", "#FCEEB5", "#EE786E", "#e0feff"]}
        />
        <Rig />
        <EffectComposer>
          <Bloom mipmapBlur luminanceThreshold={1} radius={0.6} />
        </EffectComposer>
      </Canvas>
    </div>
  )
}

function Lines({
  dash,
  count,
  colors,
  radius = 50,
  rand = THREE.MathUtils.randFloatSpread
}: {
  dash: number
  count: number
  colors: (string | number[])[]
  radius?: number
  rand?: (range: number) => number
}) {
  const lines = useMemo(() => {
    return Array.from({ length: count }, () => {
      const pos = new THREE.Vector3(rand(radius), rand(radius), rand(radius))
      const points = Array.from({ length: 10 }, () =>
        pos.add(new THREE.Vector3(rand(radius), rand(radius), rand(radius))).clone()
      )
      const curve = new THREE.CatmullRomCurve3(points).getPoints(300)
      return {
        color: colors[Math.floor(colors.length * Math.random())],
        width: Math.max(radius / 100, (radius / 50) * Math.random()),
        speed: Math.max(0.1, 1 * Math.random()),
        curve: curve.flatMap((point) => point.toArray())
      }
    })
  }, [colors, count, radius, rand])
  return lines.map((props, index) => <Fatline key={index} dash={dash} {...props} />)
}

function Fatline({
  curve,
  width,
  color,
  speed,
  dash
}: {
  curve: number[]
  width: number
  color: string | number[]
  speed: number
  dash: number
}) {
  const ref = useRef<THREE.Mesh>(null)
  useFrame((_state, delta) => {
    if (!ref.current) return
    const mat = ref.current.material as THREE.ShaderMaterial & { dashOffset: number }
    mat.dashOffset -= (delta * speed) / 10
  })
  return (
    <mesh ref={ref}>
      <meshLineGeometry points={curve} />
      <meshLineMaterial
        transparent
        lineWidth={width}
        color={color}
        depthWrite={false}
        dashArray={0.25}
        dashRatio={dash}
        toneMapped={false}
      />
    </mesh>
  )
}

function Rig({ radius = 20 }: { radius?: number }) {
  useFrame((state, dt) => {
    easing.damp3(
      state.camera.position,
      [
        Math.sin(state.pointer.x) * radius,
        Math.atan(state.pointer.y) * radius,
        Math.cos(state.pointer.x) * radius
      ],
      0.25,
      dt
    )
    state.camera.lookAt(0, 0, 0)
  })
  return null
}
