"use client"

/**
 * GLTFScene — R3F GLTF 模型加载示例
 * 移植自 three.js 官方示例 webgl-loader-gltf
 * 支持 HDR 环境光、动画播放、相机自适应
 * @author AaronZZH & Kiro
 */

import { Environment, OrbitControls, useAnimations, useGLTF } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { Suspense, useEffect, useRef, useState } from "react"
import * as THREE from "three"

import { $url } from "@/lib/utils"

const MODELS = [{ label: "Damaged Helmet", path: $url.cdn("/assets/models/glb/DamagedHelmet.glb") }]

function Model({ path }: { path: string }) {
  const { scene, animations } = useGLTF(path)
  const groupRef = useRef<THREE.Group>(null)
  const { actions } = useAnimations(animations, groupRef)

  useEffect(() => {
    // 播放所有动画
    for (const action of Object.values(actions)) action?.play()
  }, [actions])

  // 自动居中缩放
  useEffect(() => {
    const box = new THREE.Box3().setFromObject(scene)
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    scene.position.sub(center)
    scene.scale.setScalar(2 / maxDim)
  }, [scene])

  return (
    <group ref={groupRef}>
      <primitive object={scene} />
    </group>
  )
}

function Scene({ modelPath }: { modelPath: string }) {
  const controlsRef = useRef<React.ComponentRef<typeof OrbitControls>>(null)

  useFrame(() => {
    controlsRef.current?.update()
  })

  return (
    <>
      <ambientLight intensity={0.8} />
      <directionalLight position={[5, 5, 5]} intensity={2} />
      <Environment
        files={$url.cdn("/assets/hdri/potsdamer_platz_1k.hdr")}
        background
        backgroundBlurriness={0.05}
      />
      <OrbitControls ref={controlsRef} enableDamping minDistance={1} maxDistance={10} />
      <Suspense fallback={null}>
        <Model path={modelPath} />
      </Suspense>
    </>
  )
}

export function GLTFScene() {
  const [current, setCurrent] = useState(MODELS[0]?.path)

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2">
        {MODELS.map((m) => (
          <button
            type="button"
            key={m.path}
            onClick={() => setCurrent(m.path)}
            className={`rounded px-3 py-1 text-sm ${
              current === m.path ? "bg-primary text-primary-foreground" : "border hover:bg-accent"
            }`}
          >
            {m.label}
          </button>
        ))}
      </div>
      <div className="h-[520px] w-full overflow-hidden rounded-lg">
        <Canvas camera={{ position: [-1.8, 0.6, 2.7], fov: 45 }} gl={{ antialias: true }}>
          <Scene modelPath={current} />
        </Canvas>
      </div>
    </div>
  )
}
