/**
 * HeroPostprocessingBackground — 首页首屏 Three.js 后处理背景
 * @author AaronZZH & Kiro
 */

"use client"

import { Icosahedron, Line, MeshDistortMaterial, PerspectiveCamera } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { Bloom, DepthOfField, EffectComposer, Noise, Vignette } from "@react-three/postprocessing"
import { Suspense, useMemo, useRef } from "react"
import { CatmullRomCurve3, Color, type Group, MathUtils, type Mesh, Vector3 } from "three"

export type HeroBackgroundVariant = "streams" | "spheres"

interface StreamConfig {
  color: string
  offset: [number, number, number]
  rotation: [number, number, number]
}

const STREAMS: StreamConfig[] = [
  { color: "#38bdf8", offset: [-2.8, -0.4, -1.6], rotation: [0.1, 0.0, -0.14] },
  { color: "#22c55e", offset: [0.2, 0.5, -2.1], rotation: [-0.06, 0.16, 0.08] },
  { color: "#f59e0b", offset: [2.6, -0.2, -1.8], rotation: [0.16, -0.12, 0.12] },
  { color: "#f43f5e", offset: [0.9, -0.95, -2.4], rotation: [-0.08, -0.2, -0.08] }
]

const SPHERE_POSITIONS: [number, number, number][] = [
  [-4, 20, -12],
  [-10, 12, -4],
  [-11, -12, -23],
  [-16, -6, -10],
  [12, -2, -3],
  [13, 4, -12],
  [14, -2, -23],
  [8, 10, -20]
]

function createStreamPoints(index: number): Vector3[] {
  const anchors = Array.from({ length: 7 }, (_, pointIndex) => {
    const x = (pointIndex - 3) * 0.95
    const y = Math.sin(pointIndex * 0.92 + index * 0.75) * 0.42
    const z = Math.cos(pointIndex * 0.68 + index * 0.45) * 0.3
    return new Vector3(x, y, z)
  })

  return new CatmullRomCurve3(anchors).getPoints(80)
}

function AgentStream({ config, index }: { config: StreamConfig; index: number }) {
  const groupRef = useRef<Group>(null)
  const points = useMemo(() => createStreamPoints(index), [index])

  useFrame(({ clock }) => {
    const group = groupRef.current
    if (!group) return
    const elapsed = clock.getElapsedTime()
    group.rotation.y = config.rotation[1] + Math.sin(elapsed * 0.24 + index) * 0.08
    group.position.y = config.offset[1] + Math.sin(elapsed * 0.42 + index * 0.7) * 0.1
  })

  return (
    <group
      ref={groupRef}
      position={config.offset}
      rotation={[config.rotation[0], config.rotation[1], config.rotation[2]]}
    >
      <Line points={points} color={config.color} lineWidth={2.4} transparent opacity={0.85} />
      <Line points={points} color={config.color} lineWidth={7} transparent opacity={0.12} />
    </group>
  )
}

function NeuralLattice() {
  const groupRef = useRef<Group>(null)
  const materialColor = useMemo(() => new Color("#94a3b8"), [])

  useFrame(({ clock }) => {
    const group = groupRef.current
    if (!group) return
    const elapsed = clock.getElapsedTime()
    group.rotation.x = -0.65 + Math.sin(elapsed * 0.08) * 0.035
    group.rotation.z = -0.12 + Math.cos(elapsed * 0.11) * 0.035
  })

  return (
    <group ref={groupRef} position={[0, -1.35, -2.9]} rotation={[-0.65, 0, -0.12]}>
      <gridHelper args={[8, 28, materialColor, materialColor]} />
    </group>
  )
}

function StreamsScene() {
  return (
    <>
      <PerspectiveCamera makeDefault position={[0, 0.15, 5.2]} fov={45} />
      <color attach="background" args={["#050816"]} />
      <ambientLight intensity={0.42} />
      <pointLight position={[-3, 2, 4]} intensity={2.4} color="#38bdf8" />
      <pointLight position={[3, -1, 3]} intensity={1.8} color="#f59e0b" />
      <NeuralLattice />
      {STREAMS.map((config, index) => (
        <AgentStream key={config.color} config={config} index={index} />
      ))}
      <EffectComposer>
        <DepthOfField focusDistance={0.012} focalLength={0.012} bokehScale={0.65} height={480} />
        <Bloom luminanceThreshold={0.16} luminanceSmoothing={0.88} intensity={0.72} height={300} />
        <Noise opacity={0.012} />
        <Vignette eskil={false} offset={0.16} darkness={0.68} />
      </EffectComposer>
    </>
  )
}

function DistortedSphere({
  position,
  scale = 1,
  color = "#0b0b0f"
}: {
  position: [number, number, number]
  scale?: number
  color?: string
}) {
  return (
    <Icosahedron args={[1, 4]} position={position} scale={scale}>
      <MeshDistortMaterial
        color={color}
        roughness={0.18}
        metalness={0.86}
        clearcoat={1}
        clearcoatRoughness={0.2}
        distort={0.38}
        speed={1.6}
      />
    </Icosahedron>
  )
}

function MainSphere() {
  const meshRef = useRef<Mesh>(null)

  useFrame(({ clock, mouse }) => {
    const mesh = meshRef.current
    if (!mesh) return
    mesh.rotation.z = clock.getElapsedTime() * 0.32
    mesh.rotation.y = MathUtils.lerp(mesh.rotation.y, mouse.x * Math.PI * 0.26, 0.08)
    mesh.rotation.x = MathUtils.lerp(mesh.rotation.x, mouse.y * Math.PI * 0.2, 0.08)
  })

  return (
    <Icosahedron args={[1.25, 5]} ref={meshRef} position={[0, -0.02, 0]} scale={1.02}>
      <MeshDistortMaterial
        color="#050506"
        emissive="#101827"
        emissiveIntensity={0.18}
        roughness={0.12}
        metalness={1}
        clearcoat={1}
        clearcoatRoughness={0.16}
        distort={0.36}
        speed={1.25}
      />
    </Icosahedron>
  )
}

function FloatingSpheres() {
  const sphereRefs = useRef<(Mesh | null)[]>([])

  useFrame(() => {
    for (const sphere of sphereRefs.current) {
      if (!sphere) continue
      sphere.position.y += 0.018
      if (sphere.position.y > 19) sphere.position.y = -18
      sphere.rotation.x += 0.028
      sphere.rotation.y += 0.035
      sphere.rotation.z += 0.014
    }
  })

  return (
    <>
      <MainSphere />
      {SPHERE_POSITIONS.map((position, index) => (
        <Icosahedron
          args={[1, 4]}
          key={`${position.join("-")}-${index}`}
          position={position}
          ref={(mesh) => {
            sphereRefs.current[index] = mesh
          }}
        >
          <MeshDistortMaterial
            color="#07070a"
            roughness={0.16}
            metalness={0.96}
            clearcoat={1}
            clearcoatRoughness={0.24}
            distort={0.32}
            speed={1.1}
          />
        </Icosahedron>
      ))}
    </>
  )
}

function SpheresScene() {
  return (
    <>
      <PerspectiveCamera makeDefault position={[0, 0, 3]} fov={50} />
      <color attach="background" args={["#050505"]} />
      <fog attach="fog" args={["#151515", 8, 30]} />
      <ambientLight intensity={0.48} />
      <directionalLight position={[2.4, 3.2, 4]} intensity={3.8} color="#ffffff" />
      <pointLight position={[-3, -1.5, 3]} intensity={3.2} color="#38bdf8" />
      <pointLight position={[3.2, 1.2, 2.5]} intensity={2.4} color="#f59e0b" />
      <Suspense fallback={null}>
        <FloatingSpheres />
        <DistortedSphere position={[-2.6, -1.8, -2.8]} scale={0.34} color="#101827" />
        <DistortedSphere position={[2.7, 1.5, -3.2]} scale={0.28} color="#171717" />
      </Suspense>
      <EffectComposer multisampling={0} enableNormalPass={false}>
        <DepthOfField focusDistance={0.01} focalLength={0.016} bokehScale={0.9} height={480} />
        <Bloom luminanceThreshold={0.05} luminanceSmoothing={0.9} intensity={0.82} height={300} />
        <Noise opacity={0.012} />
        <Vignette eskil={false} offset={0.18} darkness={0.82} />
      </EffectComposer>
    </>
  )
}

interface HeroPostprocessingBackgroundProps {
  variant?: HeroBackgroundVariant
}

export function HeroPostprocessingBackground({
  variant = "streams"
}: HeroPostprocessingBackgroundProps) {
  const isSpheres = variant === "spheres"

  return (
    <div
      className={
        isSpheres
          ? "pointer-events-none absolute inset-0 overflow-hidden bg-[#050505]"
          : "pointer-events-none absolute inset-0 overflow-hidden bg-[#050816]"
      }
    >
      <Canvas
        dpr={[1, 1.5]}
        gl={{
          alpha: false,
          antialias: !isSpheres,
          depth: !isSpheres,
          powerPreference: "high-performance",
          stencil: false
        }}
        camera={
          isSpheres ? { position: [0, 0, 3], fov: 50 } : { position: [0, 0.15, 5.2], fov: 45 }
        }
      >
        {isSpheres ? <SpheresScene /> : <StreamsScene />}
      </Canvas>
      <div
        className={
          isSpheres
            ? "absolute inset-0 bg-[radial-gradient(circle_at_50%_38%,rgba(5,5,5,0.12)_0%,rgba(5,5,5,0.38)_54%,rgba(5,5,5,0.92)_100%)]"
            : "absolute inset-0 bg-[radial-gradient(circle_at_50%_30%,transparent_0%,rgba(5,8,22,0.34)_58%,rgba(5,8,22,0.86)_100%)]"
        }
      />
      <div className="absolute inset-0 bg-background/5" />
    </div>
  )
}
