"use client"

import { Preload } from "@react-three/drei"
import { Canvas } from "@react-three/fiber"
import * as THREE from "three"

import { t } from "@/lib/tunnel"

export default function Scene({ ...props }) {
  // Everything defined in here will persist between route changes, only children are swapped
  return (
    <Canvas {...props} onCreated={(state) => (state.gl.toneMapping = THREE.AgXToneMapping)}>
      {/* t.In 组件中内容在此处渲染 */}
      <t.Out />
      <Preload all />
    </Canvas>
  )
}
