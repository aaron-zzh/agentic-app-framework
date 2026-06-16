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
      const x = (i / (POINT_COUNT - 1) - 0.5) * 14 // -7 ~ 7
      const y =
        Math.sin(x * 1.5 + phaseX + t * phaseT) * amp +
        Math.sin(x * 3 + phaseX * 2 + t * (phaseT * 1.3)) * amp * 0.4
      pos[i * 3] = x
      pos[i * 3 + 1] = y
      pos[i * 3 + 2] = 0
    }
    geometry.current.getAttribute("position").needsUpdate = true
  })

  return <line geometry={geometry.current} material={material.current} />
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
      amplitudeRef.current = (avg / 255) * 0.8
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
    <Canvas
      camera={{ position: [0, 0, 2.5], fov: 55 }}
      gl={{ alpha: true, antialias: true }}
      style={{ background: "transparent" }}
    >
      {LINES.map((line, i) => (
        <WaveformLine key={i} amplitude={amplitudeRef} {...line} />
      ))}
    </Canvas>
  )
}
