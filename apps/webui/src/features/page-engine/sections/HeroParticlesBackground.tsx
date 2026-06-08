/**
 * HeroParticlesBackground — CSS3DSprite 粒子群动画背景
 * 移植自 three.js 官方示例 css3d-sprites，使用 CSS3DRenderer 渲染 512 个粒子
 * 粒子在平面、立方体、随机、球体四种形态间循环过渡
 * @author AaronZZH & Kiro
 */

"use client"

import { useEffect, useRef } from "react"
import * as THREE from "three"
import { TrackballControls } from "three/examples/jsm/controls/TrackballControls.js"
import TWEEN from "three/examples/jsm/libs/tween.module.js"
import { CSS3DRenderer, CSS3DSprite } from "three/examples/jsm/renderers/CSS3DRenderer.js"

const PARTICLES_TOTAL = 512

/** 预计算四种形态的粒子目标坐标 */
function buildPositions(): number[] {
  const positions: number[] = []

  // 平面（正弦波）
  const amountX = 16
  const amountZ = 32
  const sepPlane = 150
  const offsetX = ((amountX - 1) * sepPlane) / 2
  const offsetZ = ((amountZ - 1) * sepPlane) / 2
  for (let i = 0; i < PARTICLES_TOTAL; i++) {
    const x = (i % amountX) * sepPlane
    const z = Math.floor(i / amountX) * sepPlane
    const y = (Math.sin(x * 0.5) + Math.sin(z * 0.5)) * 200
    positions.push(x - offsetX, y, z - offsetZ)
  }

  // 立方体
  const amount = 8
  const sepCube = 150
  const offset = ((amount - 1) * sepCube) / 2
  for (let i = 0; i < PARTICLES_TOTAL; i++) {
    const x = (i % amount) * sepCube
    const y = Math.floor((i / amount) % amount) * sepCube
    const z = Math.floor(i / (amount * amount)) * sepCube
    positions.push(x - offset, y - offset, z - offset)
  }

  // 随机
  for (let i = 0; i < PARTICLES_TOTAL; i++) {
    positions.push(
      Math.random() * 4000 - 2000,
      Math.random() * 4000 - 2000,
      Math.random() * 4000 - 2000
    )
  }

  // 球体
  const radius = 750
  for (let i = 0; i < PARTICLES_TOTAL; i++) {
    const phi = Math.acos(-1 + (2 * i) / PARTICLES_TOTAL)
    const theta = Math.sqrt(PARTICLES_TOTAL * Math.PI) * phi
    positions.push(
      radius * Math.cos(theta) * Math.sin(phi),
      radius * Math.sin(theta) * Math.sin(phi),
      radius * Math.cos(phi)
    )
  }

  return positions
}

/** 创建单个粒子 DOM 元素（小圆点，不需要外部 sprite 图片） */
function createParticleEl(): HTMLElement {
  const el = document.createElement("div")
  el.style.cssText =
    "width:6px;height:6px;border-radius:50%;background:radial-gradient(circle,rgba(56,189,248,0.9) 0%,rgba(56,189,248,0) 100%);"
  return el
}

export function HeroParticlesBackground() {
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const container = containerRef.current
    if (!container) return

    // 场景、相机
    const camera = new THREE.PerspectiveCamera(
      75,
      container.clientWidth / container.clientHeight,
      1,
      5000
    )
    camera.position.set(600, 400, 1500)
    camera.lookAt(0, 0, 0)

    const scene = new THREE.Scene()

    // 创建粒子对象
    const objects: CSS3DSprite[] = []
    for (let i = 0; i < PARTICLES_TOTAL; i++) {
      const sprite = new CSS3DSprite(createParticleEl())
      sprite.position.set(
        Math.random() * 4000 - 2000,
        Math.random() * 4000 - 2000,
        Math.random() * 4000 - 2000
      )
      scene.add(sprite)
      objects.push(sprite)
    }

    const positions = buildPositions()

    // 渲染器
    const renderer = new CSS3DRenderer()
    renderer.setSize(container.clientWidth, container.clientHeight)
    container.appendChild(renderer.domElement)

    // 交互控制
    const controls = new TrackballControls(camera, renderer.domElement)

    // 形态过渡
    let current = 0

    function transition() {
      const offset = current * PARTICLES_TOTAL * 3
      const duration = 2000

      for (let i = 0, j = offset; i < PARTICLES_TOTAL; i++, j += 3) {
        const obj = objects[i]
        if (!obj) continue
        new TWEEN.Tween(obj.position)
          .to(
            { x: positions[j], y: positions[j + 1], z: positions[j + 2] },
            Math.random() * duration + duration
          )
          .easing(TWEEN.Easing.Exponential.InOut)
          .start()
      }

      // 用哑计时器触发下一次形态过渡
      const timer = { t: 0 }
      new TWEEN.Tween(timer)
        .to({ t: 1 }, duration * 3)
        .onComplete(transition)
        .start()

      current = (current + 1) % 4
    }

    transition()

    // 动画循环
    let animId: number
    function animate() {
      animId = requestAnimationFrame(animate)
      TWEEN.update()
      controls.update()

      const time = performance.now()
      for (const obj of objects) {
        const scale = Math.sin((Math.floor(obj.position.x) + time) * 0.002) * 0.3 + 1
        obj.scale.set(scale, scale, scale)
      }

      renderer.render(scene, camera)
    }
    animate()

    // 响应窗口变化
    function onResize() {
      if (!container) return
      camera.aspect = container.clientWidth / container.clientHeight
      camera.updateProjectionMatrix()
      renderer.setSize(container.clientWidth, container.clientHeight)
    }
    window.addEventListener("resize", onResize)

    return () => {
      window.removeEventListener("resize", onResize)
      cancelAnimationFrame(animId)
      controls.dispose()
      // CSS3DRenderer 没有 dispose，手动清除 DOM
      if (container.contains(renderer.domElement)) {
        container.removeChild(renderer.domElement)
      }
    }
  }, [])

  return <div ref={containerRef} className="pointer-events-none absolute inset-0 bg-[#000510]" />
}
