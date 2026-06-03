"use client"

import { extend, useFrame } from "@react-three/fiber"
import { MeshLineGeometry, MeshLineMaterial } from "meshline"
import { useMemo, useRef } from "react"
import { CatmullRomCurve3, Vector3 } from "three"

extend({ MeshLineGeometry, MeshLineMaterial })

const r = () => Math.max(0.2, Math.random())

function Fatline({ curve, color }: { curve: number[]; color: string }) {
  const material = useRef<{ uniforms: { dashOffset: { value: number } } }>(null)

  useFrame((_state, delta) => {
    if (material.current) {
      material.current.uniforms.dashOffset.value -= delta / 100
    }
  })

  return (
    <mesh>
      <meshLineGeometry points={curve} />
      <meshLineMaterial
        ref={material}
        transparent
        lineWidth={0.01}
        color={color}
        dashArray={0.1}
        dashRatio={0.99}
      />
    </mesh>
  )
}

export default function Fireflies({
  count,
  colors,
  radius = 10
}: {
  count: number
  colors: string[]
  radius?: number
}) {
  const lines = useMemo(
    () =>
      Array.from({ length: count }, () => {
        const pos = new Vector3(Math.sin(0) * radius * r(), Math.cos(0) * radius * r(), 0)
        const points = Array.from({ length: 30 }, (_, index) => {
          const angle = (index / 20) * Math.PI * 2
          return pos
            .add(new Vector3(Math.sin(angle) * radius * r(), Math.cos(angle) * radius * r(), 0))
            .clone()
        })
        // flatMap 展开 Vector3 为 number[] 与 meshline points 类型兼容
        const curve = new CatmullRomCurve3(points).getPoints(100).flatMap((v) => v.toArray())
        return {
          color: colors[Math.floor(colors.length * Math.random())],
          curve
        }
      }),
    [count, radius, colors]
  )

  return (
    <group position={[-radius * 2, -radius, 0]}>
      {lines.map((props, index) => (
        <Fatline key={index} {...props} />
      ))}
    </group>
  )
}
