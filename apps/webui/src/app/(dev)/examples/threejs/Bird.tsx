"use client"

import { useAnimations, useGLTF } from "@react-three/drei"
import { useFrame } from "@react-three/fiber"
import { useEffect } from "react"
import type * as THREE from "three"
import type { AnimationClip, BufferGeometry, Material, Object3D } from "three"

interface BirdProps {
  speed: number
  factor: number
  url: string
  position?: [number, number, number]
  rotation?: [number, number, number]
}

export default function Bird({ speed, factor, url, ...props }: BirdProps) {
  const { nodes, animations } = useGLTF(url) as unknown as {
    nodes: Record<
      string,
      Object3D & {
        morphTargetDictionary?: Record<string, number>
        morphTargetInfluences?: number[]
        geometry: BufferGeometry
        material: Material
      }
    >
    animations: AnimationClip[]
  }
  const { ref, mixer } = useAnimations(animations)

  useEffect(() => {
    if (ref.current) {
      void mixer.clipAction(animations[0], ref.current).play()
    }
  }, [mixer, animations, ref])

  useFrame((_state, delta) => {
    const group = ref.current as THREE.Group | null
    if (group) {
      group.rotation.y += Math.sin((delta * factor) / 2) * Math.cos((delta * factor) / 2) * 1.5
      mixer.update(delta * speed)
    }
  })

  return (
    <group ref={ref as React.RefObject<THREE.Group>}>
      <scene name="Scene" {...props}>
        <mesh
          name="Object_0"
          morphTargetDictionary={nodes.Object_0.morphTargetDictionary}
          morphTargetInfluences={nodes.Object_0.morphTargetInfluences}
          rotation={[1.5707964611537577, 0, 0]}
        >
          <bufferGeometry attach="geometry" {...nodes.Object_0.geometry} />
          <meshStandardMaterial
            attach="material"
            {...nodes.Object_0.material}
            name="Material_0_COLOR_0"
          />
        </mesh>
      </scene>
    </group>
  )
}
