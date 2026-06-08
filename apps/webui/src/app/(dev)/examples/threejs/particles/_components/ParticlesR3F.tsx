"use client"

/**
 * ParticlesR3F — R3F + WebGL 版粒子背景（对标 CSS3DSprite 版）
 * 512 个粒子在平面、立方体、随机、球体四种形态间 TWEEN 过渡
 * 使用 Points + BufferGeometry，接入 Bloom 后处理
 * @author AaronZZH & Kiro
 */

import { OrbitControls } from "@react-three/drei"
import { Canvas, useFrame, useThree } from "@react-three/fiber"
import { Bloom, EffectComposer } from "@react-three/postprocessing"
import { useEffect, useMemo, useRef } from "react"
import * as THREE from "three"
import TWEEN from "three/examples/jsm/libs/tween.module.js"

const PARTICLES_TOTAL = 512

function buildTargets(): Float32Array[] {
  const targets: Float32Array[] = []

  const make = (fn: (i: number) => [number, number, number]) => {
    const arr = new Float32Array(PARTICLES_TOTAL * 3)
    for (let i = 0; i < PARTICLES_TOTAL; i++) {
      const [x, y, z] = fn(i)
      arr[i * 3] = x
      arr[i * 3 + 1] = y
      arr[i * 3 + 2] = z
    }
    return arr
  }

  // 平面（正弦波）
  const amountX = 16,
    amountZ = 32,
    sep = 150
  const offsetX = ((amountX - 1) * sep) / 2
  const offsetZ = ((amountZ - 1) * sep) / 2
  targets.push(
    make((i) => {
      const x = (i % amountX) * sep
      const z = Math.floor(i / amountX) * sep
      const y = (Math.sin(x * 0.5) + Math.sin(z * 0.5)) * 200
      return [x - offsetX, y, z - offsetZ]
    })
  )

  // 立方体
  const a = 8,
    sc = 150,
    off = ((a - 1) * sc) / 2
  targets.push(
    make((i) => [
      (i % a) * sc - off,
      Math.floor((i / a) % a) * sc - off,
      Math.floor(i / (a * a)) * sc - off
    ])
  )

  // 随机
  targets.push(
    make(() => [
      Math.random() * 4000 - 2000,
      Math.random() * 4000 - 2000,
      Math.random() * 4000 - 2000
    ])
  )

  // 球体
  const r = 750
  targets.push(
    make((i) => {
      const phi = Math.acos(-1 + (2 * i) / PARTICLES_TOTAL)
      const theta = Math.sqrt(PARTICLES_TOTAL * Math.PI) * phi
      return [
        r * Math.cos(theta) * Math.sin(phi),
        r * Math.sin(theta) * Math.sin(phi),
        r * Math.cos(phi)
      ]
    })
  )

  return targets
}

function Particles() {
  const { size } = useThree()
  const geoRef = useRef<THREE.BufferGeometry>(null)
  const groupRef = useRef<THREE.Group>(null)

  // 慢速 Y 轴旋转
  useFrame((_, delta) => {
    if (groupRef.current) groupRef.current.rotation.y += delta * 0.08
  })

  const { positions, targets } = useMemo(() => {
    const pos = new Float32Array(PARTICLES_TOTAL * 3)
    for (let i = 0; i < PARTICLES_TOTAL * 3; i++) pos[i] = Math.random() * 4000 - 2000
    return { positions: pos, targets: buildTargets() }
  }, [])

  // 当前插值用的代理对象数组
  const proxies = useMemo(
    () =>
      Array.from({ length: PARTICLES_TOTAL }, (_, i) => ({
        x: positions[i * 3] ?? 0,
        y: positions[i * 3 + 1] ?? 0,
        z: positions[i * 3 + 2] ?? 0
      })),
    [positions]
  )

  const currentShape = useRef(0)

  useEffect(() => {
    let active = true

    function transition() {
      if (!active) return
      const target = targets[currentShape.current]
      if (!target) return
      const duration = 2000

      for (let i = 0; i < PARTICLES_TOTAL; i++) {
        const proxy = proxies[i]
        if (!proxy) continue
        new TWEEN.Tween(proxy)
          .to(
            { x: target[i * 3], y: target[i * 3 + 1], z: target[i * 3 + 2] },
            Math.random() * duration + duration
          )
          .easing(TWEEN.Easing.Exponential.InOut)
          .start()
      }

      const timer = { t: 0 }
      new TWEEN.Tween(timer)
        .to({ t: 1 }, duration * 3)
        .onComplete(transition)
        .start()
      currentShape.current = (currentShape.current + 1) % 4
    }

    transition()
    return () => {
      active = false
    }
  }, [proxies, targets])

  useFrame(() => {
    TWEEN.update()
    const geo = geoRef.current
    if (!geo) return
    const attr = geo.attributes.position as THREE.BufferAttribute
    for (let i = 0; i < PARTICLES_TOTAL; i++) {
      const p = proxies[i]
      if (!p) continue
      attr.setXYZ(i, p.x, p.y, p.z)
    }
    attr.needsUpdate = true
  })

  // 每个粒子的颜色（固定蓝色调，带随机亮度）
  const colors = useMemo(() => {
    const c = new Float32Array(PARTICLES_TOTAL * 3)
    const color = new THREE.Color()
    for (let i = 0; i < PARTICLES_TOTAL; i++) {
      color.setHSL(0.55 + Math.random() * 0.15, 0.8, 0.5 + Math.random() * 0.3)
      c[i * 3] = color.r
      c[i * 3 + 1] = color.g
      c[i * 3 + 2] = color.b
    }
    return c
  }, [])

  // 粒子大小随位置脉冲（在 useFrame 里做）
  const sizes = useMemo(() => new Float32Array(PARTICLES_TOTAL).fill(12), [])

  useFrame(() => {
    const geo = geoRef.current
    if (!geo) return
    const sizeAttr = geo.attributes.size as THREE.BufferAttribute | undefined
    if (!sizeAttr) return
    const time = performance.now()
    for (let i = 0; i < PARTICLES_TOTAL; i++) {
      const p = proxies[i]
      if (!p) continue
      sizeAttr.array[i] = (Math.sin((Math.floor(p.x) + time) * 0.002) * 0.3 + 1) * 12
    }
    sizeAttr.needsUpdate = true
  })

  // ShaderMaterial：球形法线光照粒子
  const material = useMemo(
    () =>
      new THREE.ShaderMaterial({
        uniforms: { size: { value: size.height / 60 } },
        vertexShader: /* glsl */ `
      attribute float size;
      attribute vec3 color;
      varying vec3 vColor;
      void main() {
        vColor = color;
        vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
        gl_PointSize = size * (600.0 / -mvPosition.z);
        gl_Position = projectionMatrix * mvPosition;
      }
    `,
        fragmentShader: /* glsl */ `
      varying vec3 vColor;
      void main() {
        vec2 uv = (gl_PointCoord - 0.5) * 2.0;
        float r2 = dot(uv, uv);
        if (r2 > 1.0) discard;
        vec3 normal = normalize(vec3(uv, sqrt(1.0 - r2)));
        vec3 light = normalize(vec3(-0.5, 0.8, 0.5));
        float diffuse = max(dot(normal, light), 0.0);
        float specular = pow(max(dot(reflect(-light, normal), vec3(0.0, 0.0, 1.0)), 0.0), 32.0);
        vec3 col = vColor * (0.25 + 0.75 * diffuse) + vec3(specular * 0.4);
        gl_FragColor = vec4(col, 1.0);
      }
    `,
        transparent: false,
        depthWrite: false,
        blending: THREE.AdditiveBlending
      }),
    [size.height]
  )

  return (
    <group ref={groupRef}>
      <points material={material}>
        <bufferGeometry ref={geoRef}>
          <bufferAttribute attach="attributes-position" args={[positions, 3]} />
          <bufferAttribute attach="attributes-color" args={[colors, 3]} />
          <bufferAttribute attach="attributes-size" args={[sizes, 1]} />
        </bufferGeometry>
      </points>
    </group>
  )
}

export function ParticlesR3F() {
  return (
    <div className="h-full w-full bg-[#000510]">
      <Canvas
        camera={{ position: [600, 400, 1500], fov: 75, near: 1, far: 5000 }}
        gl={{ antialias: false, powerPreference: "high-performance" }}
        dpr={[1, 1.5]}
      >
        <color attach="background" args={["#000510"]} />
        <Particles />
        <OrbitControls enablePan={false} />
        <EffectComposer>
          <Bloom luminanceThreshold={0.05} intensity={0.8} luminanceSmoothing={0.9} />
        </EffectComposer>
      </Canvas>
    </div>
  )
}
