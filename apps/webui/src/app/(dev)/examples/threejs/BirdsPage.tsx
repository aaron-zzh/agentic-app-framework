"use client"

// A growing collection of useful helpers and fully functional, ready-made abstractions for @react-three/fiber.
import { OrbitControls } from "@react-three/drei"
import { Canvas } from "@react-three/fiber"
import { Suspense, useMemo } from "react"

import Bird from "./Bird"

export default function BirdsPage() {
  const birds = useMemo(
    () =>
      Array.from({ length: 10 }, (_, index) => {
        const x = (15 + Math.random() * 30) * (Math.round(Math.random()) ? -1 : 1)
        const y = -10 + Math.random() * 20
        const z = -5 + Math.random() * 10
        const bird = ["stork", "parrot", "flamingo"][Math.round(Math.random() * 2)]
        const speed = bird === "stork" ? 0.5 : bird === "flamingo" ? 2 : 5
        const factor =
          bird === "stork"
            ? 0.5 + Math.random()
            : bird === "flamingo"
              ? 0.25 + Math.random()
              : 1 + Math.random() - 0.5

        return {
          key: index,
          position: [x, y, z] as [number, number, number],
          rotation: [0, x > 0 ? Math.PI : 0, 0] as [number, number, number],
          speed,
          factor,
          url: `/glb/${bird}.glb`
        }
      }),
    []
  )

  return (
    <Canvas camera={{ position: [0, 0, 35] }}>
      <ambientLight intensity={2} />
      <pointLight position={[40, 40, 40]} />
      <OrbitControls />
      <Suspense fallback={null}>
        {birds.map((props) => (
          <Bird {...props} key={props.key} />
        ))}
      </Suspense>
    </Canvas>
  )
}
