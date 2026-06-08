"use client"

/**
 * VideoScene — R3F 视频纹理示例
 * 移植自 three.js 官方示例 webgl-materials-video
 * 200 个彩色小方块贴上视频纹理，色相随时间旋转，周期性爆炸/复位
 * @author AaronZZH & Kiro
 */

import { Canvas, useFrame } from "@react-three/fiber"
import { Bloom, EffectComposer } from "@react-three/postprocessing"
import { useEffect, useMemo, useRef } from "react"
import * as THREE from "three"

import { $url } from "@/lib/utils"

const VIDEO_URL = $url.cdn("/assets/videos/demo.mp4")
const XGRID = 20
const YGRID = 10

/** 构建带 UV 偏移的 BoxGeometry */
function makeGeometry(ix: number, iy: number): THREE.BufferGeometry {
  const xsize = 480 / XGRID
  const ysize = 204 / YGRID
  const geo = new THREE.BoxGeometry(xsize, ysize, xsize)
  const uvs = geo.attributes.uv?.array as Float32Array | undefined
  if (!uvs) return geo
  const ux = 1 / XGRID
  const uy = 1 / YGRID
  for (let k = 0; k < uvs.length; k += 2) {
    uvs[k] = ((uvs[k] ?? 0) + ix) * ux
    uvs[k + 1] = ((uvs[k + 1] ?? 0) + iy) * uy
  }
  return geo
}

interface CubeData {
  ix: number
  iy: number
  hue: number
  saturation: number
  dx: number
  dy: number
}

function VideoCubes({ texture }: { texture: THREE.VideoTexture }) {
  const cubes = useMemo<CubeData[]>(() => {
    const list: CubeData[] = []
    for (let i = 0; i < XGRID; i++) {
      for (let j = 0; j < YGRID; j++) {
        list.push({
          ix: i,
          iy: j,
          hue: i / XGRID,
          saturation: 1 - j / YGRID,
          dx: 0.001 * (0.5 - Math.random()),
          dy: 0.001 * (0.5 - Math.random())
        })
      }
    }
    return list
  }, [])

  const meshRefs = useRef<(THREE.Mesh | null)[]>([])
  const matRefs = useRef<(THREE.MeshLambertMaterial | null)[]>([])
  const counter = useRef(1)
  const xsize = 480 / XGRID
  const ysize = 204 / YGRID

  useFrame(() => {
    const time = Date.now() * 0.00005
    const c = counter.current

    for (let i = 0; i < cubes.length; i++) {
      const cube = cubes[i]
      if (!cube) continue
      const mat = matRefs.current[i]
      const mesh = meshRefs.current[i]
      if (!mat || !mesh) continue

      const h = ((360 * (cube.hue + time)) % 360) / 360
      mat.color.setHSL(h, cube.saturation, 0.5)

      if (c % 1000 > 200) {
        mesh.rotation.x += 10 * cube.dx
        mesh.rotation.y += 10 * cube.dy
        mesh.position.x -= 150 * cube.dx
        mesh.position.y += 150 * cube.dy
        mesh.position.z += 300 * cube.dx
      }

      if (c % 1000 === 0) {
        cube.dx *= -1
        cube.dy *= -1
      }
    }

    counter.current++
  })

  return (
    <>
      {cubes.map((cube, idx) => (
        <mesh
          key={idx}
          ref={(m) => {
            meshRefs.current[idx] = m
          }}
          geometry={makeGeometry(cube.ix, cube.iy)}
          position={[(cube.ix - XGRID / 2) * xsize, (cube.iy - YGRID / 2) * ysize, 0]}
        >
          <meshLambertMaterial
            ref={(m) => {
              matRefs.current[idx] = m
            }}
            map={texture}
            color={new THREE.Color().setHSL(cube.hue, cube.saturation, 0.5)}
          />
        </mesh>
      ))}
    </>
  )
}

function Scene() {
  const texture = useMemo(() => {
    const video = document.createElement("video")
    video.src = VIDEO_URL
    video.loop = true
    video.muted = true
    video.crossOrigin = "anonymous"
    video.playsInline = true
    video.play().catch(() => {})
    video.addEventListener("play", () => {
      video.currentTime = 3
    })
    const tex = new THREE.VideoTexture(video)
    tex.colorSpace = THREE.SRGBColorSpace
    return tex
  }, [])

  const mouse = useRef({ x: 0, y: 0 })

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      mouse.current.x = e.clientX - window.innerWidth / 2
      mouse.current.y = (e.clientY - window.innerHeight / 2) * 0.3
    }
    window.addEventListener("mousemove", onMove)
    return () => window.removeEventListener("mousemove", onMove)
  }, [])

  useFrame(({ camera }) => {
    camera.position.x += (mouse.current.x - camera.position.x) * 0.05
    camera.position.y += (-mouse.current.y - camera.position.y) * 0.05
    camera.lookAt(0, 0, 0)
  })

  return (
    <>
      <directionalLight position={[0.5, 1, 1]} intensity={3} />
      <VideoCubes texture={texture} />
      <EffectComposer>
        <Bloom luminanceThreshold={0.2} intensity={1.3} />
      </EffectComposer>
    </>
  )
}

export function VideoScene() {
  return (
    <div className="h-[480px] w-full cursor-move">
      <Canvas camera={{ position: [0, 0, 500], fov: 40, near: 1, far: 10000 }}>
        <Scene />
      </Canvas>
    </div>
  )
}
