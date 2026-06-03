import { DemoContainer } from "../_components/DemoContainer"

export default function MinimalPage() {
  return (
    <div className="space-y-4 p-5">
      <h1 className="font-bold text-2xl">react-three-fiber 原理演示</h1>
      <p className="text-gray-600">使用自定义 Reconciler 将 JSX 转换为 Three.js 对象树</p>

      <div className="rounded border bg-gray-50 p-4">
        <h2 className="mb-2 font-bold">核心机制</h2>
        <pre className="rounded bg-black p-3 text-green-400 text-xs">
          {`JSX:  <mesh><boxGeometry /><meshStandardMaterial /></mesh>
  ↓ React Reconciler
API:  const mesh = new THREE.Mesh()
      const geo = new THREE.BoxGeometry()
      const mat = new THREE.MeshStandardMaterial()
      mesh.add(geo)
      mesh.add(mat)`}
        </pre>
      </div>

      <DemoContainer>
        {/* @ts-ignore */}
        <mesh />
      </DemoContainer>
    </div>
  )
}
