"use client"

import { useFrame } from "@react-three/fiber"
import type * as React from "react"
import { useRef, useState } from "react"
import type * as THREE from "three"

export default function Box(props: Omit<React.JSX.IntrinsicElements["mesh"], "scale">) {
  const mesh = useRef<THREE.Mesh>(null)
  const [hovered, setHover] = useState(false)
  const [active, setActive] = useState(false)

  useFrame(() => {
    if (mesh.current) {
      mesh.current.rotation.x = mesh.current.rotation.y += 0.01
    }
  })

  return (
    // biome-ignore lint/a11y/noStaticElementInteractions: Three.js mesh 是 3D 交互对象，不适用 HTML a11y 规则
    <mesh
      {...props}
      ref={mesh}
      scale={active ? 6 : 5}
      onClick={() => setActive(!active)}
      onPointerOver={() => setHover(true)}
      onPointerOut={() => setHover(false)}
    >
      <boxGeometry args={[1, 1, 1]} />
      <meshStandardMaterial color={hovered ? "hotpink" : "#2f74c0"} />
    </mesh>
  )
}
