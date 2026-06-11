/**
 * 动画鸟群 3D 场景——参考 demo BirdsPage
 * @author AaronZZH & Kiro
 */

"use client"

import { OrbitControls, useAnimations, useGLTF } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { Suspense, useEffect, useMemo } from "react"
import { cdn } from "@/lib/utils/asset-url"

function Bird({
  speed,
  factor,
  url,
  ...props
}: {
  speed: number
  factor: number
  url: string
  position: [number, number, number]
  rotation: [number, number, number]
}) {
  const { nodes, animations } = useGLTF(url) as unknown as {
    nodes: Record<
      string,
      {
        morphTargetDictionary?: { [key: string]: number }
        morphTargetInfluences?: number[]
        geometry: object
        material: object
      }
    >
    animations: object[]
  }
  const { ref, mixer } = useAnimations(animations as never[])

  useEffect(() => {
    if (animations[0]) {
      mixer.clipAction(animations[0] as never).play()
    }
  }, [mixer, animations])

  useFrame((_state, delta) => {
    if (ref.current) {
      ;(ref.current as { rotation: { y: number } }).rotation.y +=
        Math.sin((delta * factor) / 2) * Math.cos((delta * factor) / 2) * 1.5
      mixer.update(delta * speed)
    }
  })

  const mesh = nodes.Object_0
  return (
    <group ref={ref as never}>
      <scene {...props}>
        {mesh && (
          <mesh
            name="Object_0"
            morphTargetDictionary={mesh.morphTargetDictionary}
            morphTargetInfluences={mesh.morphTargetInfluences}
            rotation={[Math.PI / 2, 0, 0]}
          >
            <bufferGeometry attach="geometry" {...(mesh.geometry as object)} />
            <meshStandardMaterial attach="material" {...(mesh.material as object)} />
          </mesh>
        )}
      </scene>
    </group>
  )
}

export default function BirdsScene() {
  const birds = useMemo(
    () =>
      Array.from({ length: 10 }, (_, i) => {
        const x = (15 + Math.random() * 30) * (Math.round(Math.random()) ? -1 : 1)
        const y = -10 + Math.random() * 20
        const z = -5 + Math.random() * 10
        const type = ["stork", "parrot", "flamingo"][Math.round(Math.random() * 2)]
        const speed = type === "stork" ? 0.5 : type === "flamingo" ? 2 : 5
        const factor =
          type === "stork"
            ? 0.5 + Math.random()
            : type === "flamingo"
              ? 0.25 + Math.random()
              : 1 + Math.random() - 0.5
        return {
          key: i,
          position: [x, y, z] as [number, number, number],
          rotation: [0, x > 0 ? Math.PI : 0, 0] as [number, number, number],
          speed,
          factor,
          url: cdn(`/assets/models/glb/${type}.glb`)
        }
      }),
    []
  )

  return (
    <Canvas camera={{ position: [0, 0, 35] }} className="size-full">
      <ambientLight intensity={2} />
      <pointLight position={[40, 40, 40]} />
      <OrbitControls />
      <Suspense fallback={null}>
        {birds.map((props) => (
          <Bird {...props} key={props.key} />
        ))}
      </Suspense>
    </Canvas>
  )
}
