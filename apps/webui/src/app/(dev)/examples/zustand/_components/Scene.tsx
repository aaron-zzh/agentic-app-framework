import { Plane, useAspect, useTexture } from "@react-three/drei"
import { Canvas, useFrame } from "@react-three/fiber"
import { DepthOfField, EffectComposer, Vignette } from "@react-three/postprocessing"
import Image from "next/image"
import type { DepthOfFieldEffect } from "postprocessing"
import { MaskFunction } from "postprocessing"
import { Suspense, useLayoutEffect, useRef, useState } from "react"
import { ErrorBoundary } from "react-error-boundary"
import type * as THREE from "three"
import { MathUtils, SRGBColorSpace, Vector3 } from "three"
import { $url } from "@/lib/utils"

declare module "@react-three/fiber" {
  interface ThreeElements {
    layerMaterial: {
      ref?: React.Ref<unknown>
      movement?: THREE.Vector3
      textr?: THREE.Texture
      factor?: number
      wiggle?: number
      scale?: number
    }
  }
}

import type * as React from "react"

// 导入场景资源图片
import bearUrl from "../resources/bear.png"
import bgUrl from "../resources/bg.jpg"
import groundUrl from "../resources/ground.png"
import leaves1Url from "../resources/leaves1.png"
import leaves2Url from "../resources/leaves2.png"
import starsUrl from "../resources/stars.png"
import Fireflies from "./Fireflies"
// 导入自定义材质
import "@/lib/materials/layerMaterial"

/**
 * 主要的 3D 场景体验组件
 * 创建多层视差效果的森林场景，包含背景、星空、地面、熊、树叶等层次
 */
function Experience() {
  // 按资源真实宽高比计算缩放，避免 1600x1000 的图片被拉伸到 2200x1000。
  const scaleN = useAspect(1600, 1000, 1.05)
  const scaleW = useAspect(1600, 1000, 1.05)

  // 批量加载所有纹理资源，提高性能
  const textures = useTexture([
    bgUrl.src,
    starsUrl.src,
    groundUrl.src,
    bearUrl.src,
    leaves1Url.src,
    leaves2Url.src
  ])

  // 设置纹理颜色空间为 sRGB，避免图片发白
  // Three.js r152+ 默认使用线性颜色空间进行渲染
  // 图片文件（PNG/JPG）通常是 sRGB 编码的
  // 如果不设置 colorSpace = SRGBColorSpace，Three.js 会将 sRGB 图片当作线性数据处理，导致颜色变淡发白
  // 设置后，Three.js 会自动进行 sRGB → 线性的转换，保证颜色正确
  textures.forEach((texture) => {
    texture.colorSpace = SRGBColorSpace
  })

  // 场景组的引用，用于整体变换
  const group = useRef<THREE.Group>(null)
  const layersRef = useRef<THREE.Mesh[]>([])
  // 鼠标移动向量，用于视差效果
  const [movement] = useState(() => new Vector3())
  // 临时向量，避免每帧创建新对象
  const [temp] = useState(() => new Vector3())

  // 定义场景的各个层次，从后到前排列
  const layers = [
    // 0: 背景层 (最远)
    { texture: textures[0], x: 0, y: 0, z: 0, factor: 0.005, scale: scaleW },
    // 1: 星空层
    { texture: textures[1], x: 0, y: 0, z: 10, factor: 0.005, scale: scaleW },
    // 2: 地面层
    { texture: textures[2], x: 0, y: 0, z: 20, scale: scaleW },
    // 3: 熊层 (主角)
    {
      texture: textures[3],
      x: 0,
      y: 0,
      z: 30,
      scaleFactor: 0.83, // 缩小熊的尺寸
      scale: scaleN
    },
    // 4: 前景树叶层 1
    {
      texture: textures[4],
      x: 0,
      y: 0,
      z: 40,
      factor: 0.03, // 视差因子
      scaleFactor: 1,
      wiggle: 0.6, // 摆动效果强度
      scale: scaleW
    },
    // 5: 前景树叶层 2 (最前)
    {
      texture: textures[5],
      x: -20,
      y: -20,
      z: 49,
      factor: 0.04,
      scaleFactor: 1.3, // 放大前景
      wiggle: 1, // 最强摆动效果
      scale: scaleW
    }
  ]

  // 每帧更新动画
  useFrame((state, delta) => {
    // 平滑插值鼠标位置，创建视差移动效果
    movement.lerp(temp.set(state.pointer.x, state.pointer.y * 0.2, 0), 0.2)

    // 根据鼠标位置调整整个场景的位置和旋转
    if (group.current) {
      group.current.position.x = MathUtils.lerp(
        group.current.position.x || 0,
        state.pointer.x * 20,
        0.05
      )
      group.current.rotation.x = MathUtils.lerp(
        group.current.rotation.x || 0,
        state.pointer.y / 20,
        0.05
      )
      group.current.rotation.y = MathUtils.lerp(
        group.current.rotation.y || 0,
        -state.pointer.x / 2,
        0.05
      )

      // 更新前景树叶层的时间 uniform，用于摆动动画
      const layer4 = layersRef.current[4] as THREE.Mesh & { uniforms: { time: { value: number } } }
      const layer5 = layersRef.current[5] as THREE.Mesh & { uniforms: { time: { value: number } } }
      if (layer4?.uniforms && layer5?.uniforms) {
        layer4.uniforms.time.value = layer5.uniforms.time.value += delta
      }
    }
  }, 1) // 优先级设为 1

  return (
    <group ref={group}>
      {/* 萤火虫效果：20只橙色萤火虫，飞行半径80 */}
      <Fireflies count={20} radius={80} colors={["orange"]} />

      {/* 渲染所有图层 */}
      {layers.map(({ scale, texture, factor = 0, scaleFactor = 1, wiggle = 0, x, y, z }, i) => (
        <Plane
          scale={scale}
          args={[1, 1, wiggle ? 10 : 1, wiggle ? 10 : 1]}
          position={[x, y, z]}
          key={i}
        >
          <layerMaterial
            movement={movement}
            textr={texture}
            factor={factor}
            ref={(el: unknown) => {
              layersRef.current[i] = el as THREE.Mesh
            }}
            wiggle={wiggle}
            scale={scaleFactor}
          />
        </Plane>
      ))}
    </group>
  )
}

/**
 * 后处理效果组件
 * 添加景深和晕影效果，增强视觉层次感
 */
function Effects() {
  const ref = useRef<DepthOfFieldEffect>(null)

  useLayoutEffect(() => {
    if (!ref.current) return
    // biome-ignore lint/suspicious/noExplicitAny: 访问 drei EffectComposer 内部属性
    const maskMaterial = (ref.current as any).maskPass?.getFullscreenMaterial()
    if (maskMaterial) maskMaterial.maskFunction = MaskFunction.MULTIPLY_RGB_SET_ALPHA
  })
  return (
    <EffectComposer enableNormalPass={false} multisampling={0}>
      <DepthOfField
        ref={ref}
        target={[0, 0, 30]}
        worldFocusRange={20}
        bokehScale={8}
        focalLength={0.1}
        width={1024}
      />
      <Vignette>{null}</Vignette>
    </EffectComposer>
  )
}

/**
 * 降级场景组件
 * 当 WebGL 不可用或出错时显示的静态图片
 */
function FallbackScene() {
  return (
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "#010101"
      }}
    >
      <Image
        src={$url.cdn("/assets/images/ogimage.jpg")}
        alt="Zustand Bear"
        fill
        style={{
          objectFit: "cover"
        }}
      />
    </div>
  )
}

/**
 * 主场景组件
 * 整合 3D Canvas、错误边界、Suspense 等功能
 */
export default function Scene() {
  return (
    <ErrorBoundary FallbackComponent={FallbackScene}>
      <Canvas
        orthographic // 使用正交投影，避免透视变形
        dpr={[1, 2]} // 按设备像素比渲染，限制最高 2 倍以兼顾清晰度和性能
        gl={{
          antialias: false // 关闭抗锯齿以提高性能
        }}
        camera={{
          zoom: 5, // 相机缩放
          position: [0, 0, 200], // 相机位置
          far: 300, // 远裁剪面
          near: 50 // 近裁剪面
        }}
        onCreated={(state) => {
          // 将事件连接到根元素，确保鼠标交互正常工作
          state.events.connect?.(document.getElementById("root"))
        }}
        fallback={<FallbackScene />}
      >
        <Suspense fallback={null}>
          {/* Suspense 防止 React Strict Mode 在开发环境下的双重挂载导致纹理加载器重复工作，避免 GPU 资源冲突和 WebGL 上下文丢失 */}
          <Experience />
        </Suspense>

        {/* 后处理效果 */}
        <Effects />
      </Canvas>
    </ErrorBoundary>
  )
}
