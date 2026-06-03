"use client"

import { Canvas, useFrame } from "@react-three/fiber"
import { useEffect, useRef, useState } from "react"
import * as THREE from "three"

/**
 * 原生 Three.js 开发方式示例
 * 命令式编程，手动管理所有对象和生命周期
 */
function VanillaThreeExample() {
  const mountRef = useRef<HTMLDivElement>(null)
  const sceneRef = useRef<{
    scene?: THREE.Scene
    camera?: THREE.PerspectiveCamera
    renderer?: THREE.WebGLRenderer
    cube?: THREE.Mesh
    animationId?: number
  }>({})

  useEffect(() => {
    if (!mountRef.current) return

    // 1. 手动创建场景、相机、渲染器
    const scene = new THREE.Scene()
    const camera = new THREE.PerspectiveCamera(75, 400 / 300, 0.1, 1000)
    const renderer = new THREE.WebGLRenderer({ antialias: true })

    renderer.setSize(400, 300)
    renderer.setClearColor(0x222222)
    mountRef.current.appendChild(renderer.domElement)

    // 2. 手动创建几何体、材质、网格
    const geometry = new THREE.BoxGeometry(1, 1, 1)
    const material = new THREE.MeshBasicMaterial({
      color: 0x00ff00,
      wireframe: true
    })
    const cube = new THREE.Mesh(geometry, material)

    // 3. 手动添加到场景
    scene.add(cube)
    camera.position.z = 5

    // 4. 手动编写动画循环
    function animate() {
      const animationId = requestAnimationFrame(animate)
      sceneRef.current.animationId = animationId

      // 手动更新对象属性
      cube.rotation.x += 0.01
      cube.rotation.y += 0.01

      renderer.render(scene, camera)
    }
    animate()

    // 5. 保存引用用于清理
    sceneRef.current = { scene, camera, renderer, cube }

    // 6. 手动清理资源
    return () => {
      if (sceneRef.current.animationId) {
        cancelAnimationFrame(sceneRef.current.animationId)
      }
      if (sceneRef.current.renderer) {
        mountRef.current?.removeChild(sceneRef.current.renderer.domElement)
        sceneRef.current.renderer.dispose()
      }
      if (sceneRef.current.cube) {
        sceneRef.current.cube.geometry.dispose()
        ;(sceneRef.current.cube.material as THREE.Material).dispose()
      }
    }
  }, [])

  return (
    <div>
      <h3>原生 Three.js 方式</h3>
      <div ref={mountRef} style={{ border: "1px solid #ccc" }} />
      <p>✅ 完全控制 ❌ 代码冗长 ❌ 手动管理生命周期</p>
    </div>
  )
}

/**
 * react-three-fiber 开发方式示例
 * 声明式编程，React 自动管理生命周期
 */
function FiberExample() {
  const [wireframe, setWireframe] = useState(true)
  const [color, setColor] = useState("#00ff00")

  return (
    <div>
      <h3>react-three-fiber 方式</h3>

      {/* 控制面板 - 展示 React 状态管理的优势 */}
      <div style={{ marginBottom: 10 }}>
        <label>
          <input
            type="checkbox"
            checked={wireframe}
            onChange={(e) => setWireframe(e.target.checked)}
          />
          线框模式
        </label>
        <input
          type="color"
          value={color}
          onChange={(e) => setColor(e.target.value)}
          style={{ marginLeft: 10 }}
        />
      </div>

      {/* 3D 场景 - 声明式定义 */}
      <Canvas
        style={{ width: 400, height: 300, border: "1px solid #ccc" }}
        camera={{ position: [0, 0, 5] }}
      >
        {/* 直接在 JSX 中声明 3D 对象 */}
        <RotatingCube wireframe={wireframe} color={color} />
      </Canvas>

      <p>✅ 声明式 ✅ 自动管理 ✅ React 生态</p>
    </div>
  )
}

/**
 * 旋转立方体组件 - 展示组件化开发
 */
function RotatingCube({ wireframe, color }: { wireframe: boolean; color: string }) {
  const meshRef = useRef<THREE.Mesh>(null)

  // 使用 useFrame 钩子进行动画 - 自动管理动画循环
  useFrame((_state, delta) => {
    if (meshRef.current) {
      meshRef.current.rotation.x += delta
      meshRef.current.rotation.y += delta
    }
  })

  return (
    <mesh ref={meshRef}>
      {/* 几何体和材质作为子组件 */}
      <boxGeometry args={[1, 1, 1]} />
      <meshBasicMaterial color={color} wireframe={wireframe} />
    </mesh>
  )
}

/**
 * 高级示例：展示 react-three-fiber 的强大功能
 */
function AdvancedFiberExample() {
  const [count, setCount] = useState(5)

  return (
    <div>
      <h3>高级功能展示</h3>

      <div style={{ marginBottom: 10 }}>
        <label>
          立方体数量: {count}
          <input
            type="range"
            min="1"
            max="20"
            value={count}
            onChange={(e) => setCount(Number(e.target.value))}
            style={{ marginLeft: 10 }}
          />
        </label>
      </div>

      <Canvas
        style={{ width: 400, height: 300, border: "1px solid #ccc" }}
        camera={{ position: [0, 0, 10] }}
      >
        <ambientLight intensity={0.5} />
        <pointLight position={[10, 10, 10]} />

        {/* 动态生成多个立方体 - 展示 React 的列表渲染 */}
        {Array.from({ length: count }, (_, i) => (
          <AnimatedCube
            key={i}
            position={[((i % 5) - 2) * 2, Math.floor(i / 5) * 2 - 1, 0]}
            color={`hsl(${(i * 360) / count}, 70%, 50%)`}
          />
        ))}
      </Canvas>

      <p>✅ 动态渲染 ✅ 组件复用 ✅ 状态驱动</p>
    </div>
  )
}

function AnimatedCube({ position, color }: { position: [number, number, number]; color: string }) {
  const meshRef = useRef<THREE.Mesh>(null)
  const [hovered, setHovered] = useState(false)

  useFrame((state) => {
    if (meshRef.current) {
      meshRef.current.rotation.x = state.clock.elapsedTime
      meshRef.current.rotation.y = state.clock.elapsedTime * 0.5
      meshRef.current.scale.setScalar(hovered ? 1.2 : 1)
    }
  })

  return (
    <mesh
      ref={meshRef}
      position={position}
      onPointerOver={() => setHovered(true)}
      onPointerOut={() => setHovered(false)}
    >
      <boxGeometry args={[0.8, 0.8, 0.8]} />
      <meshStandardMaterial color={color} />
    </mesh>
  )
}

/**
 * 开发方式对比演示组件
 */
export default function DevelopmentComparison() {
  return (
    <div style={{ padding: 20, fontFamily: "Arial, sans-serif" }}>
      <h1>react-three-fiber 开发方式对比</h1>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20, marginBottom: 30 }}>
        <VanillaThreeExample />
        <FiberExample />
      </div>

      <AdvancedFiberExample />

      <div style={{ marginTop: 30, padding: 20, backgroundColor: "#f5f5f5", borderRadius: 8 }}>
        <h3>核心差异总结</h3>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20 }}>
          <div>
            <h4>原生 Three.js</h4>
            <ul>
              <li>命令式编程</li>
              <li>手动管理对象生命周期</li>
              <li>手动编写动画循环</li>
              <li>手动处理资源清理</li>
              <li>代码冗长，容易出错</li>
            </ul>
          </div>
          <div>
            <h4>react-three-fiber</h4>
            <ul>
              <li>声明式编程</li>
              <li>React 自动管理生命周期</li>
              <li>useFrame 钩子处理动画</li>
              <li>自动资源清理</li>
              <li>组件化，易于复用和维护</li>
            </ul>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 20, padding: 15, backgroundColor: "#e8f4fd", borderRadius: 8 }}>
        <h4>运行时转换机制</h4>
        <pre style={{ backgroundColor: "#fff", padding: 10, borderRadius: 4, overflow: "auto" }}>
          {`// JSX 写法
<mesh position={[0, 0, 0]}>
  <boxGeometry args={[1, 1, 1]} />
  <meshBasicMaterial color="red" />
</mesh>

// react-three-fiber 运行时转换为：
const mesh = new THREE.Mesh()
mesh.position.set(0, 0, 0)

const geometry = new THREE.BoxGeometry(1, 1, 1)
const material = new THREE.MeshBasicMaterial({ color: 'red' })

mesh.geometry = geometry
mesh.material = material
scene.add(mesh)`}
        </pre>
      </div>
    </div>
  )
}
