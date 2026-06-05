"use client"

import { OrbitControls } from "@react-three/drei"
import { Canvas } from "@react-three/fiber"

import Box from "./Box"

const BoxesPage = () => {
  return (
    <>
      <h1>点击正方形</h1>
      <div style={{ height: 500 }}>
        <Canvas camera={{ position: [0, 0, 35] }}>
          {/* <ambientLight intensity={2} /> */}
          <ambientLight intensity={Math.PI / 2} />
          <spotLight
            position={[10, 10, 10]}
            angle={0.15}
            penumbra={1}
            decay={0}
            intensity={Math.PI}
          />
          {/* <pointLight position={[40, 40, 40]} /> */}
          <pointLight position={[-10, -10, -10]} decay={0} intensity={Math.PI} />
          <Box position={[10, 0, 0]} />
          <Box position={[-10, 0, 0]} />
          <Box position={[0, 10, 0]} />
          <Box position={[0, -10, 0]} />
          <OrbitControls />
        </Canvas>
      </div>
    </>
  )
}

export default BoxesPage
