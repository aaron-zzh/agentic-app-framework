import { shaderMaterial } from "@react-three/drei"
import { extend, useFrame } from "@react-three/fiber"
import { useImperativeHandle, useRef } from "react"
import * as THREE from "three"
import fragment from "./glsl/shader.frag"
import vertex from "./glsl/shader.vert"

const ShaderImpl = shaderMaterial(
  {
    time: 0,
    color: new THREE.Color(0.05, 0.0, 0.025)
  },
  vertex,
  fragment
)

type ShaderImplType = typeof ShaderImpl & { time: number; color: THREE.Color }

extend({ ShaderImpl })

declare module "@react-three/fiber" {
  interface ThreeElements {
    shaderImpl: Partial<ShaderImplType> & {
      attach?: string
      glsl?: number
      ref?: React.Ref<THREE.ShaderMaterial>
    }
  }
}

const Shader = (
  props: Omit<React.ComponentProps<"shaderImpl">, "children"> & {
    ref?: React.Ref<THREE.ShaderMaterial>
  }
) => {
  const localRef = useRef<THREE.ShaderMaterial & { time: number }>(null)

  useImperativeHandle(props.ref, () => localRef.current as THREE.ShaderMaterial & { time: number })

  useFrame((_, delta) => {
    if (localRef.current) localRef.current.time += delta
  })
  return (
    <shaderImpl
      ref={localRef}
      glsl={THREE.GLSL3 as unknown as number}
      {...props}
      attach="material"
    />
  )
}

export default Shader
