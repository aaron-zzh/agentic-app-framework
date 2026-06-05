"use client"

import { OrbitControls } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { useRef, useState } from "react"
import type * as THREE from "three"
import { DemoContainer } from "../_components/DemoContainer"

function SpinningBox() {
  const mesh = useRef<THREE.Mesh>(null)
  const [hovered, setHovered] = useState(false)
  const [active, setActive] = useState(false)

  useFrame(() => {
    if (mesh.current) mesh.current.rotation.y += 0.01
  })

  return (
    // biome-ignore lint/a11y/noStaticElementInteractions: Three.js mesh
    <mesh
      ref={mesh}
      scale={active ? 1.4 : 1}
      onClick={() => setActive((v) => !v)}
      onPointerOver={() => setHovered(true)}
      onPointerOut={() => setHovered(false)}
    >
      <boxGeometry args={[2, 2, 2]} />
      <meshStandardMaterial color={hovered ? "hotpink" : "#2f74c0"} />
    </mesh>
  )
}

export default function MinimalPage() {
  return (
    <div className="space-y-4 p-5">
      <h1 className="font-bold text-2xl">react-three-fiber 原理演示</h1>
      <p className="text-gray-600">使用自定义 Reconciler 将 JSX 转换为 Three.js 对象树</p>

      <div className="rounded border bg-gray-50 p-4">
        <h2 className="mb-2 font-bold">核心机制</h2>
        <pre className="rounded bg-black p-3 text-green-400 text-xs">
          {`JSX:  <mesh><boxGeometry /><meshStandardMaterial /></mesh>
  ↓ React Reconciler
API:  mesh.geometry = new THREE.BoxGeometry()
      mesh.material = new THREE.MeshStandardMaterial()
      scene.add(mesh)`}
        </pre>
      </div>

      <DemoContainer>
        {/* @ts-ignore */}
        <mesh />
      </DemoContainer>

      <div className="rounded border bg-gray-50 p-4">
        <h2 className="mb-2 font-bold">实际渲染效果（点击变大，悬停变色）</h2>
        <div style={{ height: 400 }}>
          <Canvas camera={{ position: [0, 0, 6] }}>
            <ambientLight intensity={0.5} />
            <pointLight position={[10, 10, 10]} />
            <SpinningBox />
            <OrbitControls />
          </Canvas>
        </div>
      </div>
    </div>
  )
}
