"use client"

import { Line, MeshDistortMaterial, useCursor, useGLTF } from "@react-three/drei"
import { useFrame } from "@react-three/fiber"
import { useRouter } from "next/navigation"
import { useMemo, useRef, useState } from "react"
import * as THREE from "three"

export const Blob = ({
  route = "/demo/threejs/demo/",
  ...props
}: { route?: string } & Record<string, unknown>) => {
  const router = useRouter()
  const [hovered, hover] = useState(false)
  useCursor(hovered)
  return (
    //biome-ignore lint/a11y/noStaticElementInteractions: R3F mesh 不是 DOM 元素
    <mesh
      onClick={() => router.push(route)}
      onPointerOver={() => hover(true)}
      onPointerOut={() => hover(false)}
      {...props}
    >
      <sphereGeometry args={[1, 64, 64]} />
      <MeshDistortMaterial roughness={0.5} color={hovered ? "hotpink" : "#1fb2f5"} />
    </mesh>
  )
}

export const Logo = ({
  route = "/demo/threejs/demo/blob",
  ...props
}: { route?: string } & Record<string, unknown>) => {
  const mesh = useRef<THREE.Group>(null)
  const router = useRouter()

  const [hovered, hover] = useState(false)
  const points = useMemo(
    () => new THREE.EllipseCurve(0, 0, 3, 1.15, 0, 2 * Math.PI, false, 0).getPoints(100),
    []
  )

  useCursor(hovered)
  useFrame((state, delta) => {
    if (!mesh.current) return
    const t = state.clock.getElapsedTime()
    mesh.current.rotation.y = Math.sin(t) * (Math.PI / 8)
    mesh.current.rotation.x = Math.cos(t) * (Math.PI / 8)
    mesh.current.rotation.z -= delta / 4
  })

  return (
    <group ref={mesh} {...props}>
      <Line worldUnits points={points} color="#1fb2f5" lineWidth={0.15} />
      <Line worldUnits points={points} color="#1fb2f5" lineWidth={0.15} rotation={[0, 0, 1]} />
      <Line worldUnits points={points} color="#1fb2f5" lineWidth={0.15} rotation={[0, 0, -1]} />
      {/* biome-ignore lint/a11y/noStaticElementInteractions: R3F mesh 不是 DOM 元素 */}
      <mesh
        onClick={() => router.push(route)}
        onPointerOver={() => hover(true)}
        onPointerOut={() => hover(false)}
      >
        <sphereGeometry args={[0.55, 64, 64]} />
        <meshPhysicalMaterial roughness={0.5} color={hovered ? "hotpink" : "#1fb2f5"} />
      </mesh>
    </group>
  )
}

export function Duck(props: Record<string, unknown>) {
  const { scene } = useGLTF("/glb/duck.glb")
  useFrame((_state, delta) => {
    scene.rotation.y += delta
  })
  return <primitive object={scene} {...props} />
}

export function Dog(props: Record<string, unknown>) {
  const { scene } = useGLTF("/glb/dog.glb")
  return <primitive object={scene} {...props} />
}
