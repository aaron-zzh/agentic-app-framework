"use client"

import { Line, OrbitControls, Sphere, Stars } from "@react-three/drei"
import { Canvas, useFrame, useLoader } from "@react-three/fiber"
import { Suspense, useMemo, useRef, useState } from "react"
import * as THREE from "three"
import { TextureLoader } from "three"
import { $url } from "@/lib/utils"

function latLngToVec3(lat: number, lng: number, radius: number): THREE.Vector3 {
  const phi = (90 - lat) * (Math.PI / 180)
  const theta = (lng + 180) * (Math.PI / 180)
  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta)
  )
}

function ArcLine({
  from,
  to,
  color = "#facc15"
}: {
  from: THREE.Vector3
  to: THREE.Vector3
  color?: string
}) {
  const mid = from.clone().add(to).multiplyScalar(0.5)
  mid.normalize().multiplyScalar(mid.length() * 1.4)
  const curve = new THREE.QuadraticBezierCurve3(from, mid, to)
  return <Line points={curve.getPoints(60)} color={color} lineWidth={1.5} />
}

function CityDot({ position }: { position: THREE.Vector3 }) {
  return (
    <mesh position={position}>
      <sphereGeometry args={[0.025, 8, 8]} />
      <meshBasicMaterial color="#ffffff" />
    </mesh>
  )
}

// ─── 地球 ─────────────────────────────────────────────────────────────────────
function Earth({ isNight }: { isNight: boolean }) {
  const meshRef = useRef<THREE.Mesh>(null)
  const [dayMap, nightMap, normalMap] = useLoader(TextureLoader, [
    $url.cdn("/assets/images/8k_earth_daymap.jpg"),
    $url.cdn("/assets/images/8k_earth_nightmap.jpg"),
    $url.cdn("/assets/images/8k_earth_normal_map.jpg")
  ])

  // Fresnel 大气层：BackSide 视觉效果最好，NormalBlending 避免闪烁
  const atmosMaterial = useMemo(
    () =>
      new THREE.ShaderMaterial({
        vertexShader: `
          varying vec3 vNormal;
          void main() {
            vNormal = normalize(normalMatrix * normal);
            gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
          }
        `,
        fragmentShader: `
          varying vec3 vNormal;
          uniform vec3 uColor;
          void main() {
            float intensity = pow(0.65 - dot(vNormal, vec3(0.0, 0.0, 1.0)), 3.0);
            intensity = clamp(intensity, 0.0, 1.0);
            gl_FragColor = vec4(uColor, intensity * 0.9);
          }
        `,
        uniforms: {
          uColor: { value: new THREE.Color(isNight ? "#2255dd" : "#4da6ff") }
        },
        blending: THREE.NormalBlending,
        side: THREE.BackSide,
        transparent: true,
        depthWrite: false
      }),
    [isNight]
  )

  useFrame((_, delta) => {
    if (meshRef.current) meshRef.current.rotation.y += delta * 0.08
  })

  return (
    <group>
      <Sphere ref={meshRef} args={[1, 64, 64]}>
        <meshStandardMaterial
          map={isNight ? nightMap : dayMap}
          normalMap={normalMap}
          normalScale={new THREE.Vector2(2.5, 2.5)}
          roughness={isNight ? 0.9 : 0.6}
          metalness={isNight ? 0.0 : 0.15}
          emissive={new THREE.Color(isNight ? "#0a0a1a" : "#000000")}
          emissiveIntensity={isNight ? 0.3 : 0}
        />
      </Sphere>
      {/* Fresnel 大气层覆盖在地球表面外侧 */}
      <mesh scale={[1.15, 1.15, 1.15]}>
        <sphereGeometry args={[1, 64, 64]} />
        <primitive object={atmosMaterial} attach="material" />
      </mesh>
    </group>
  )
}

const ROUTES: Array<{ from: [number, number]; to: [number, number]; color: string }> = [
  { from: [40.7, -74.0], to: [51.5, -0.1], color: "#facc15" },
  { from: [35.7, 139.7], to: [22.3, 114.2], color: "#60a5fa" },
  { from: [40.7, -74.0], to: [-23.5, -46.6], color: "#ffffff" },
  { from: [51.5, -0.1], to: [1.3, 103.8], color: "#34d399" }
]

const CITIES: Array<[number, number]> = [
  [40.7, -74.0],
  [51.5, -0.1],
  [35.7, 139.7],
  [22.3, 114.2],
  [-23.5, -46.6],
  [1.3, 103.8]
]

const R = 1.01

function GlobeScene({ isNight }: { isNight: boolean }) {
  return (
    <>
      <ambientLight intensity={isNight ? 0.4 : 0.6} />
      <directionalLight position={[5, 3, 5]} intensity={isNight ? 0.8 : 1.5} />
      {isNight && <pointLight position={[-5, -3, -5]} intensity={0.3} color="#2244aa" />}
      <Stars radius={80} depth={50} count={3000} factor={3} fade />
      <Earth isNight={isNight} />
      {CITIES.map(([lat, lng]) => (
        <CityDot key={`${lat},${lng}`} position={latLngToVec3(lat, lng, R)} />
      ))}
      {ROUTES.map((r, i) => (
        <ArcLine
          key={i}
          from={latLngToVec3(r.from[0], r.from[1], R)}
          to={latLngToVec3(r.to[0], r.to[1], R)}
          color={r.color}
        />
      ))}
      <OrbitControls
        enablePan={false}
        minDistance={1.5}
        maxDistance={5}
        autoRotate
        autoRotateSpeed={0.3}
      />
    </>
  )
}

export default function GlobeView() {
  const [isNight, setIsNight] = useState(false)

  return (
    <div
      className="relative h-screen w-full overflow-hidden bg-[#060d1f]"
      style={{ height: "calc(100vh - 48px)" }}
    >
      <Canvas camera={{ position: [0, 0, 2.8], fov: 45 }} gl={{ antialias: true }}>
        <Suspense fallback={null}>
          <GlobeScene isNight={isNight} />
        </Suspense>
      </Canvas>
      <button
        type="button"
        onClick={() => setIsNight((v) => !v)}
        className="absolute right-4 bottom-4 flex items-center gap-2 rounded-full bg-white/10 px-4 py-2 text-sm text-white backdrop-blur-sm transition hover:bg-white/20"
      >
        {isNight ? "☀️ 白天" : "🌙 夜晚"}
      </button>
    </div>
  )
}
