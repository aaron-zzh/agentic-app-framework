"use client"

/**
 * VoiceWaveform3D——3D 声纹波形，透明背景，高度 60px
 * 用 R3F + meshline 渲染一条随音量振幅变化的正弦曲线
 * @author AaronZZH & Kiro
 */

import { Canvas, useFrame } from "@react-three/fiber"
import { useRef } from "react"
import * as THREE from "three"

const POINT_COUNT = 128

const LINES = [
  { color: 0x6366f1, phaseX: 0, phaseT: 4, ampScale: 1.0 }, // 靛蓝
  { color: 0x22d3ee, phaseX: 1.5, phaseT: 5, ampScale: 0.7 }, // 青色
  { color: 0xa855f7, phaseX: 3.0, phaseT: 3.5, ampScale: 0.85 }, // 紫色
  { color: 0xf472b6, phaseX: 4.5, phaseT: 6, ampScale: 0.5 } // 粉色
]

/** 单条声纹曲线 */
function WaveformLine({
  amplitude,
  color,
  phaseX,
  phaseT,
  ampScale
}: {
  amplitude: React.MutableRefObject<number>
  color: number
  phaseX: number
  phaseT: number
  ampScale: number
}) {
  const posRef = useRef<Float32Array>(new Float32Array(POINT_COUNT * 3))
  const geometry = useRef(new THREE.BufferGeometry())
  const material = useRef(new THREE.LineBasicMaterial({ color, linewidth: 1 }))

  if (!geometry.current.getAttribute("position")) {
    geometry.current.setAttribute("position", new THREE.BufferAttribute(posRef.current, 3))
  }

  useFrame(({ clock }) => {
    const t = clock.getElapsedTime()
    const amp = amplitude.current * ampScale
    const pos = posRef.current
    for (let i = 0; i < POINT_COUNT; i++) {
      // x 范围 -12~12，适配宽高比大的 canvas，曲线铺满并超出相机视野
      const x = (i / (POINT_COUNT - 1) - 0.5) * 24
      const y =
        Math.sin(x * 0.9 + phaseX + t * phaseT) * amp +
        Math.sin(x * 2.0 + phaseX * 2 + t * (phaseT * 1.3)) * amp * 0.4
      pos[i * 3] = x
      pos[i * 3 + 1] = y
      pos[i * 3 + 2] = 0
    }
    geometry.current.getAttribute("position").needsUpdate = true
  })

  const lineObj = useRef(new THREE.Line(geometry.current, material.current))
  return <primitive object={lineObj.current} />
}

interface VoiceWaveform3DProps {
  stream: MediaStream
}

export function VoiceWaveform3D({ stream }: VoiceWaveform3DProps) {
  const amplitudeRef = useRef(0)

  // 用 Web Audio API 读音量，更新到 ref（不触发 re-render）
  const analyserSetup = useRef(false)
  if (!analyserSetup.current && typeof window !== "undefined") {
    analyserSetup.current = true
    const audioCtx = new AudioContext()
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    const src = audioCtx.createMediaStreamSource(stream)
    src.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)

    const tick = () => {
      analyser.getByteFrequencyData(data)
      const lowFreq = data.slice(0, data.length / 4)
      const avg = lowFreq.reduce((s, v) => s + v, 0) / lowFreq.length
      amplitudeRef.current = (avg / 255) * 2.5
      requestAnimationFrame(tick)
    }
    tick()

    // stream 结束时清理
    stream.getTracks()[0]?.addEventListener("ended", () => {
      src.disconnect()
      audioCtx.close()
    })
  }

  return (
    <div style={{ position: "relative", width: "100%", height: "100%", overflow: "hidden" }}>
      {/* canvas 填满容器，x 范围远超相机视野，曲线两端自然被裁掉 */}
      <Canvas
        camera={{ position: [0, 0, 2.5], fov: 55 }}
        gl={{ alpha: true, antialias: true }}
        style={{
          background: "transparent",
          position: "absolute",
          top: 0,
          bottom: 0,
          left: 0,
          right: 0
        }}
      >
        {LINES.map((line, i) => (
          <WaveformLine key={i} amplitude={amplitudeRef} {...line} />
        ))}
      </Canvas>
      {/* 左侧渐变遮罩，固定 8px，仅在 canvas 内淡出曲线端点 */}
      <div
        style={{
          position: "absolute",
          left: 0,
          top: 0,
          bottom: 0,
          width: 8,
          background: "linear-gradient(to right, var(--background), transparent)",
          pointerEvents: "none"
        }}
      />
      {/* 右侧渐变遮罩，固定 8px */}
      <div
        style={{
          position: "absolute",
          right: 0,
          top: 0,
          bottom: 0,
          width: 8,
          background: "linear-gradient(to left, var(--background), transparent)",
          pointerEvents: "none"
        }}
      />
    </div>
  )
}
