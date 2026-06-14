"use client"

/**
 * WavesScene — 粒子波浪 R3F 实现
 * 2500 个粒子（50×50）构成正弦波浪场，自定义 GLSL 着色器渲染圆形粒子
 * 鼠标移动驱动摄像机视角追踪
 * @author AaronZZH & Kiro
 */

import { OrbitControls } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { useMemo, useRef } from "react"
import * as THREE from "three"

const SEPARATION = 100
const AMOUNTX = 50
const AMOUNTY = 50

const vertexShader = /* glsl */ `
  attribute float scale;
  void main() {
    vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
    gl_PointSize = scale * (300.0 / -mvPosition.z);
    gl_Position = projectionMatrix * mvPosition;
  }
`

const fragmentShader = /* glsl */ `
  uniform vec3 color;
  void main() {
    vec2 coord = gl_PointCoord - vec2(0.5);
    float dist = length(coord);
    if (dist > 0.5) discard;

    // 球体法线：将圆形点映射为半球表面法线
    vec2 uv = coord * 2.0;                          // [-1, 1]
    float z = sqrt(max(0.0, 1.0 - dot(uv, uv)));   // 保证 x²+y²+z²=1
    vec3 normal = normalize(vec3(uv, z));

    // 固定光源方向（左上前方）
    vec3 light = normalize(vec3(-0.5, 0.8, 0.5));
    float diffuse = max(dot(normal, light), 0.0);

    // 镜面高光（视线方向为 +z）
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    float specular = pow(max(dot(reflect(-light, normal), viewDir), 0.0), 32.0);

    vec3 finalColor = color * (0.25 + 0.75 * diffuse) + vec3(specular * 0.35);
    gl_FragColor = vec4(finalColor, 1.0);
  }
`

function Waves() {
  const countRef = useRef(0)
  const pointsRef = useRef<THREE.Points>(null)

  const numParticles = AMOUNTX * AMOUNTY
  const positions = useMemo(() => {
    const arr = new Float32Array(numParticles * 3)
    let i = 0
    for (let ix = 0; ix < AMOUNTX; ix++) {
      for (let iy = 0; iy < AMOUNTY; iy++) {
        arr[i] = ix * SEPARATION - (AMOUNTX * SEPARATION) / 2
        arr[i + 1] = 0
        arr[i + 2] = iy * SEPARATION - (AMOUNTY * SEPARATION) / 2
        i += 3
      }
    }
    return arr
  }, [numParticles])

  const scales = useMemo(() => new Float32Array(numParticles).fill(1), [numParticles])

  const material = useMemo(
    () =>
      new THREE.ShaderMaterial({
        uniforms: { color: { value: new THREE.Color(0xb07fa0) } },
        vertexShader,
        fragmentShader
      }),
    []
  )

  useFrame(() => {
    const pts = pointsRef.current
    if (!pts) return

    const pos = pts.geometry.attributes.position as THREE.BufferAttribute
    const sc = pts.geometry.attributes.scale as THREE.BufferAttribute
    const count = countRef.current
    let i = 0,
      j = 0

    for (let ix = 0; ix < AMOUNTX; ix++) {
      for (let iy = 0; iy < AMOUNTY; iy++) {
        pos.array[i + 1] = Math.sin((ix + count) * 0.3) * 50 + Math.sin((iy + count) * 0.5) * 50
        sc.array[j] =
          (Math.sin((ix + count) * 0.3) + 1) * 20 + (Math.sin((iy + count) * 0.5) + 1) * 20
        i += 3
        j++
      }
    }
    pos.needsUpdate = true
    sc.needsUpdate = true
    countRef.current += 0.1
  })

  return (
    <points ref={pointsRef} material={material}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" args={[positions, 3]} />
        <bufferAttribute attach="attributes-scale" args={[scales, 1]} />
      </bufferGeometry>
    </points>
  )
}

export function WavesScene() {
  return (
    <div className="h-full w-full bg-black">
      <Canvas
        camera={{ position: [0, 800, 600], fov: 60, near: 1, far: 10000 }}
        gl={{ antialias: true }}
        dpr={[1, 1.5]}
      >
        <OrbitControls enablePan={false} />
        <Waves />
      </Canvas>
    </div>
  )
}
