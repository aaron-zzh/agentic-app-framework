/**
 * Three.js 知识图渲染内核：批量绘制节点和关系，并管理 WebGL 生命周期。
 * @author AaronZZH & Kiro
 */

import {
  AmbientLight,
  Box3,
  BufferAttribute,
  BufferGeometry,
  type Camera,
  Color,
  DirectionalLight,
  DynamicDrawUsage,
  Group,
  InstancedMesh,
  LineBasicMaterial,
  LineSegments,
  Matrix4,
  MeshStandardMaterial,
  OrthographicCamera,
  PerspectiveCamera,
  Raycaster,
  Scene,
  SphereGeometry,
  SRGBColorSpace,
  Vector2,
  Vector3,
  WebGLRenderer
} from "three"
import { OrbitControls } from "three/addons/controls/OrbitControls.js"
import { validateGraph } from "./layout"
import type { LayoutWorkerRequest, LayoutWorkerResponse } from "./layout-worker-protocol"
import type {
  GraphDimension,
  GraphLayout,
  GraphPosition,
  ThreeGraphEngineOptions,
  VisualizationColor,
  VisualizationGraph,
  VisualizationNode
} from "./types"

interface PickingTreeNode {
  readonly bounds: Box3
  readonly indices?: readonly number[]
  readonly left?: PickingTreeNode
  readonly right?: PickingTreeNode
}

const DEFAULT_NODE_COLOR = 0x38bdf8
const DEFAULT_EDGE_COLOR = 0x64748b
const DEFAULT_NODE_SIZE = 5
const DEFAULT_SPACING = 44
const DEFAULT_LAYER_GAP = 18
const POINTER_CLICK_TOLERANCE = 4
const ORTHOGRAPHIC_VIEW_HEIGHT = 640

/** Three.js 知识图渲染器，所有 DOM/WebGL 操作都由实例生命周期管理。 */
export class ThreeGraphEngine<TNodeData = unknown, TEdgeData = unknown> {
  private readonly container: HTMLElement
  private readonly scene = new Scene()
  private readonly graphGroup = new Group()
  private readonly renderer: WebGLRenderer
  private readonly raycaster = new Raycaster()
  private readonly pointer = new Vector2()
  private readonly dimension: GraphDimension
  private readonly layout: GraphLayout
  private readonly nodeSize: number
  private readonly spacing: number
  private readonly layerGap: number
  private readonly maxPixelRatio: number
  private camera: OrthographicCamera | PerspectiveCamera
  private controls: OrbitControls | null = null
  private resizeObserver: ResizeObserver | null = null
  private layoutWorker: Worker | null = null
  private nodeMesh: InstancedMesh<SphereGeometry, MeshStandardMaterial> | null = null
  private edgeLines: LineSegments<BufferGeometry, LineBasicMaterial> | null = null
  private graph: VisualizationGraph<TNodeData, TEdgeData> = { nodes: [], edges: [] }
  private positions = new Map<string, GraphPosition>()
  private nodesByInstanceId: readonly VisualizationNode<TNodeData>[] = []
  private instanceIndexByNodeId = new Map<string, number>()
  private pickingTree: PickingTreeNode | null = null
  private baseNodeColors: readonly Color[] = []
  private selectedNodeId: string | null = null
  private hoveredNodeId: string | null = null
  private pointerDown: { readonly x: number; readonly y: number } | null = null
  private pendingHoverPointer: { readonly x: number; readonly y: number } | null = null
  private onNodePick?: (node: VisualizationNode<TNodeData> | null) => void
  private onNodeHover?: (node: VisualizationNode<TNodeData> | null) => void
  private onError?: (error: Error) => void
  private animationFrame: number | null = null
  private hoverAnimationFrame: number | null = null
  private layoutRequestId = 0
  private failed = false
  private disposed = false

  constructor(container: HTMLElement, options: ThreeGraphEngineOptions<TNodeData> = {}) {
    this.container = container
    this.dimension = options.dimension ?? "2.5d"
    this.layout = options.layout ?? "force"
    this.nodeSize = options.nodeSize ?? DEFAULT_NODE_SIZE
    this.spacing = options.spacing ?? DEFAULT_SPACING
    this.layerGap = options.layerGap ?? DEFAULT_LAYER_GAP
    this.maxPixelRatio = options.maxPixelRatio ?? 2
    this.onNodePick = options.onNodePick
    this.onNodeHover = options.onNodeHover
    this.onError = options.onError

    this.camera = this.createCamera()
    this.renderer = new WebGLRenderer({
      alpha: options.background === undefined,
      antialias: true,
      powerPreference: "high-performance"
    })

    try {
      this.renderer.outputColorSpace = SRGBColorSpace
      this.renderer.setPixelRatio(Math.min(globalThis.devicePixelRatio ?? 1, this.maxPixelRatio))
      if (options.background === undefined) {
        this.renderer.setClearColor(0x000000, 0)
      } else {
        this.renderer.setClearColor(toColor(options.background), 1)
      }
      this.renderer.domElement.style.display = "block"
      this.renderer.domElement.style.height = "100%"
      this.renderer.domElement.style.width = "100%"
      this.renderer.domElement.style.touchAction = "none"
      this.renderer.domElement.setAttribute("aria-hidden", "true")
      this.container.appendChild(this.renderer.domElement)

      const controls = this.createControls(this.camera)
      this.controls = controls
      this.scene.add(this.graphGroup)
      this.scene.add(new AmbientLight(0xffffff, 1.15))
      const keyLight = new DirectionalLight(0xffffff, 2.2)
      keyLight.position.set(120, 180, 260)
      this.scene.add(keyLight)
      const fillLight = new DirectionalLight(0x60a5fa, 1.1)
      fillLight.position.set(-180, -80, 120)
      this.scene.add(fillLight)

      this.renderer.domElement.addEventListener("pointerdown", this.handlePointerDown)
      this.renderer.domElement.addEventListener("pointermove", this.handlePointerMove)
      this.renderer.domElement.addEventListener("pointerup", this.handlePointerUp)
      this.renderer.domElement.addEventListener("pointerleave", this.handlePointerLeave)
      this.renderer.domElement.addEventListener("webglcontextlost", this.handleContextLost)
      controls.addEventListener("change", this.invalidate)

      const resizeObserver = new ResizeObserver(this.handleResize)
      this.resizeObserver = resizeObserver
      resizeObserver.observe(this.container)
      this.resize()

      this.layoutWorker = new Worker(new URL("./layout.worker.js", import.meta.url), {
        name: "aaf-knowledge-layout",
        type: "module"
      })
      this.layoutWorker.addEventListener("message", this.handleLayoutMessage)
      this.layoutWorker.addEventListener("error", this.handleLayoutError)
    } catch (cause) {
      this.dispose()
      throw cause
    }
  }

  /** 替换当前图数据；布局在 Worker 中完成，主线程只重建固定数量的 GPU 批对象。 */
  setGraph(graph: VisualizationGraph<TNodeData, TEdgeData>): void {
    this.assertActive()
    validateGraph(graph)
    this.layoutRequestId += 1
    this.disposeGraphResources()
    this.positions.clear()
    this.graph = graph
    this.nodesByInstanceId = graph.nodes
    this.instanceIndexByNodeId = new Map(graph.nodes.map((node, index) => [node.id, index]))
    this.pickingTree = null
    this.baseNodeColors = graph.nodes.map((node) => toColor(node.color ?? DEFAULT_NODE_COLOR))

    const nodeIds = new Set(graph.nodes.map((node) => node.id))
    if (this.selectedNodeId && !nodeIds.has(this.selectedNodeId)) this.selectedNodeId = null
    if (this.hoveredNodeId && !nodeIds.has(this.hoveredNodeId)) {
      this.hoveredNodeId = null
      this.onNodeHover?.(null)
    }

    if (graph.nodes.length === 0) {
      this.invalidate()
      return
    }

    const request: LayoutWorkerRequest = {
      requestId: this.layoutRequestId,
      nodes: graph.nodes.map((node) => ({
        id: node.id,
        ...(node.position ? { position: node.position } : {}),
        ...(node.layer === undefined ? {} : { layer: node.layer })
      })),
      edges: graph.edges.map((edge) => ({
        id: edge.id,
        source: edge.source,
        target: edge.target,
        ...(edge.strength === undefined ? {} : { strength: edge.strength })
      })),
      options: {
        dimension: this.dimension,
        layout: this.layout,
        spacing: this.spacing,
        layerGap: this.layerGap
      }
    }
    const worker = this.layoutWorker
    if (!worker) throw new Error("知识图布局 Worker 未初始化")
    worker.postMessage(request)
    this.invalidate()
  }

  /** 更新节点点击回调，不重建 WebGL 场景。 */
  setOnNodePick(handler: ((node: VisualizationNode<TNodeData> | null) => void) | undefined): void {
    this.assertActive()
    this.onNodePick = handler
  }

  /** 更新节点悬停回调，不重建 WebGL 场景。 */
  setOnNodeHover(handler: ((node: VisualizationNode<TNodeData> | null) => void) | undefined): void {
    this.assertActive()
    this.onNodeHover = handler
  }

  /** 设置业务层选中的节点，并在 GPU 实例属性中突出显示。 */
  setSelectedNodeId(nodeId: string | null | undefined): void {
    this.assertActive()
    const nextNodeId = nodeId ?? null
    if (nextNodeId === this.selectedNodeId) return
    const previousNodeId = this.selectedNodeId
    this.selectedNodeId = nextNodeId
    this.updateNodeAppearances([previousNodeId, nextNodeId])
    this.invalidate()
  }

  /** 依据当前节点包围盒调整相机视口。 */
  fitView(): void {
    this.assertActive()
    if (this.positions.size === 0) return

    const controls = this.controls
    if (!controls) throw new Error("知识图相机控制器未初始化")
    const bounds = new Box3()
    for (const position of this.positions.values()) {
      bounds.expandByPoint(new Vector3(position.x, position.y, position.z))
    }
    const center = bounds.getCenter(new Vector3())
    const size = bounds.getSize(new Vector3())
    const radius = Math.max(size.length() / 2, this.spacing * 1.5)
    controls.target.copy(center)

    if (this.camera instanceof OrthographicCamera) {
      const width = Math.max(this.container.clientWidth, 1)
      const height = Math.max(this.container.clientHeight, 1)
      const aspect = width / height
      const paddedWidth = Math.max(size.x, this.spacing * 2) * 1.35
      const paddedHeight = Math.max(size.y, this.spacing * 2) * 1.35
      const horizontalZoom = (ORTHOGRAPHIC_VIEW_HEIGHT * aspect) / paddedWidth
      const verticalZoom = ORTHOGRAPHIC_VIEW_HEIGHT / paddedHeight
      this.camera.position.set(center.x, center.y, center.z + Math.max(radius * 3, 300))
      this.camera.zoom = Math.max(0.08, Math.min(horizontalZoom, verticalZoom))
      this.camera.updateProjectionMatrix()
    } else {
      const verticalHalfFov = (this.camera.fov * Math.PI) / 360
      const horizontalHalfFov = Math.atan(Math.tan(verticalHalfFov) * this.camera.aspect)
      const limitingHalfFov = Math.max(0.1, Math.min(verticalHalfFov, horizontalHalfFov))
      const distance = (radius / Math.sin(limitingHalfFov)) * 1.25
      if (this.dimension === "3d") {
        this.camera.position.copy(center).add(new Vector3(distance, distance * 0.65, distance))
      } else {
        this.camera.position.copy(center).add(new Vector3(0, distance * 0.45, distance))
      }
      this.camera.near = Math.max(0.1, distance / 1000)
      this.camera.far = Math.max(2000, distance * 8)
      this.camera.updateProjectionMatrix()
    }
    controls.update()
    this.invalidate()
  }

  /** 根据容器尺寸更新 WebGL 画布和相机投影。 */
  resize(): void {
    this.assertActive()
    const width = this.container.clientWidth
    const height = this.container.clientHeight
    if (width <= 0 || height <= 0) return

    this.renderer.setSize(width, height, false)
    if (this.camera instanceof PerspectiveCamera) {
      this.camera.aspect = width / height
    } else if (this.camera instanceof OrthographicCamera) {
      const aspect = width / height
      this.camera.left = (-ORTHOGRAPHIC_VIEW_HEIGHT * aspect) / 2
      this.camera.right = (ORTHOGRAPHIC_VIEW_HEIGHT * aspect) / 2
      this.camera.top = ORTHOGRAPHIC_VIEW_HEIGHT / 2
      this.camera.bottom = -ORTHOGRAPHIC_VIEW_HEIGHT / 2
    }
    this.camera.updateProjectionMatrix()
    if (this.positions.size > 0) this.fitView()
    else this.invalidate()
  }

  /** 幂等释放 GPU、观察器、事件监听和 WebGL 上下文。 */
  dispose(): void {
    if (this.disposed) return
    this.disposed = true
    this.layoutRequestId += 1
    if (this.animationFrame !== null) cancelAnimationFrame(this.animationFrame)
    if (this.hoverAnimationFrame !== null) cancelAnimationFrame(this.hoverAnimationFrame)
    this.animationFrame = null
    this.hoverAnimationFrame = null
    this.pendingHoverPointer = null
    if (this.layoutWorker) {
      this.layoutWorker.removeEventListener("message", this.handleLayoutMessage)
      this.layoutWorker.removeEventListener("error", this.handleLayoutError)
      this.layoutWorker.terminate()
      this.layoutWorker = null
    }
    this.resizeObserver?.disconnect()
    this.resizeObserver = null
    if (this.controls) {
      this.controls.removeEventListener("change", this.invalidate)
      this.controls.dispose()
      this.controls = null
    }
    this.renderer.domElement.removeEventListener("pointerdown", this.handlePointerDown)
    this.renderer.domElement.removeEventListener("pointermove", this.handlePointerMove)
    this.renderer.domElement.removeEventListener("pointerup", this.handlePointerUp)
    this.renderer.domElement.removeEventListener("pointerleave", this.handlePointerLeave)
    this.renderer.domElement.removeEventListener("webglcontextlost", this.handleContextLost)
    this.disposeGraphResources()
    this.renderer.renderLists.dispose()
    this.renderer.dispose()
    this.renderer.forceContextLoss()
    if (this.renderer.domElement.parentElement === this.container) {
      this.container.removeChild(this.renderer.domElement)
    }
    this.nodesByInstanceId = []
    this.instanceIndexByNodeId.clear()
    this.pickingTree = null
    this.baseNodeColors = []
    this.positions.clear()
  }

  private createCamera(): OrthographicCamera | PerspectiveCamera {
    if (this.dimension === "2d") {
      const camera = new OrthographicCamera(-320, 320, 320, -320, 0.1, 5000)
      camera.position.set(0, 0, 700)
      return camera
    }
    const camera = new PerspectiveCamera(48, 1, 0.1, 10000)
    camera.position.set(0, 180, 520)
    return camera
  }

  private createControls(camera: Camera): OrbitControls {
    const controls = new OrbitControls(camera)
    try {
      controls.connect(this.renderer.domElement)
      controls.enableDamping = false
      controls.enablePan = true
      controls.enableRotate = this.dimension !== "2d"
      controls.minDistance = 12
      controls.maxDistance = 10000
      controls.minZoom = 0.03
      controls.maxZoom = 30
      if (this.dimension === "2.5d") {
        controls.minPolarAngle = Math.PI * 0.2
        controls.maxPolarAngle = Math.PI * 0.72
      }
      return controls
    } catch (cause) {
      controls.dispose()
      throw cause
    }
  }

  private readonly handleLayoutMessage = (event: MessageEvent<LayoutWorkerResponse>): void => {
    if (this.disposed || this.failed || event.data.requestId !== this.layoutRequestId) return
    if ("error" in event.data) {
      this.reportError(new Error(event.data.error))
      return
    }

    try {
      this.positions = new Map(event.data.positions)
      this.pickingTree = this.buildPickingTree()
      this.disposeGraphResources()
      if (this.graph.nodes.length > 0) {
        this.nodeMesh = this.createNodeMesh(this.graph.nodes)
        this.graphGroup.add(this.nodeMesh)
      }
      if (this.graph.edges.length > 0) {
        this.edgeLines = this.createEdgeLines(this.graph)
        this.graphGroup.add(this.edgeLines)
      }
      this.updateNodeInstances()
      this.fitView()
      this.invalidate()
    } catch (cause) {
      this.disposeGraphResources()
      this.reportError(cause)
    }
  }

  private readonly handleLayoutError = (event: ErrorEvent): void => {
    this.reportError(new Error(event.message || "知识图布局 Worker 执行失败"))
  }

  private createNodeMesh(
    nodes: readonly VisualizationNode<TNodeData>[]
  ): InstancedMesh<SphereGeometry, MeshStandardMaterial> {
    const geometry = new SphereGeometry(1, 14, 10)
    const material = new MeshStandardMaterial({
      metalness: 0.08,
      roughness: 0.38,
      vertexColors: true
    })
    const mesh = new InstancedMesh(geometry, material, nodes.length)
    mesh.instanceMatrix.setUsage(DynamicDrawUsage)
    mesh.frustumCulled = false
    return mesh
  }

  private createEdgeLines(
    graph: VisualizationGraph<TNodeData, TEdgeData>
  ): LineSegments<BufferGeometry, LineBasicMaterial> {
    const vertices = new Float32Array(graph.edges.length * 6)
    const colors = new Float32Array(graph.edges.length * 6)

    graph.edges.forEach((edge, index) => {
      const source = this.positions.get(edge.source)
      const target = this.positions.get(edge.target)
      if (!source || !target) return
      const offset = index * 6
      vertices.set([source.x, source.y, source.z, target.x, target.y, target.z], offset)
      const color = toColor(edge.color ?? DEFAULT_EDGE_COLOR)
      colors.set([color.r, color.g, color.b, color.r, color.g, color.b], offset)
    })

    const geometry = new BufferGeometry()
    geometry.setAttribute("position", new BufferAttribute(vertices, 3))
    geometry.setAttribute("color", new BufferAttribute(colors, 3))
    geometry.computeBoundingSphere()
    const material = new LineBasicMaterial({
      depthWrite: false,
      opacity: 0.56,
      transparent: true,
      vertexColors: true
    })
    return new LineSegments(geometry, material)
  }

  private buildPickingTree(): PickingTreeNode | null {
    if (this.graph.nodes.length === 0) return null

    const buildNode = (indices: readonly number[]): PickingTreeNode => {
      const bounds = new Box3()
      for (const index of indices) {
        const node = this.graph.nodes[index]
        const position = node ? this.positions.get(node.id) : undefined
        if (!node || !position) continue
        const radius = (node.size ?? this.nodeSize) * 1.5
        bounds.min.x = Math.min(bounds.min.x, position.x - radius)
        bounds.min.y = Math.min(bounds.min.y, position.y - radius)
        bounds.min.z = Math.min(bounds.min.z, position.z - radius)
        bounds.max.x = Math.max(bounds.max.x, position.x + radius)
        bounds.max.y = Math.max(bounds.max.y, position.y + radius)
        bounds.max.z = Math.max(bounds.max.z, position.z + radius)
      }

      if (indices.length <= 12) return { bounds, indices }

      const size = bounds.getSize(new Vector3())
      const axis: "x" | "y" | "z" =
        size.x >= size.y && size.x >= size.z ? "x" : size.y >= size.z ? "y" : "z"
      const sorted = [...indices].sort((left, right) => {
        const leftNode = this.graph.nodes[left]
        const rightNode = this.graph.nodes[right]
        const leftPosition = leftNode ? this.positions.get(leftNode.id) : undefined
        const rightPosition = rightNode ? this.positions.get(rightNode.id) : undefined
        return (leftPosition?.[axis] ?? 0) - (rightPosition?.[axis] ?? 0)
      })
      const middle = Math.floor(sorted.length / 2)
      return {
        bounds,
        left: buildNode(sorted.slice(0, middle)),
        right: buildNode(sorted.slice(middle))
      }
    }

    return buildNode(this.graph.nodes.map((_, index) => index))
  }

  private updateNodeInstances(): void {
    const mesh = this.nodeMesh
    if (!mesh) return
    const matrix = new Matrix4()
    const position = new Vector3()
    const scale = new Vector3()

    this.graph.nodes.forEach((_, index) => {
      this.writeNodeInstance(mesh, index, matrix, position, scale)
    })
    mesh.instanceMatrix.clearUpdateRanges()
    mesh.instanceMatrix.needsUpdate = true
    if (mesh.instanceColor) {
      mesh.instanceColor.clearUpdateRanges()
      mesh.instanceColor.needsUpdate = true
    }
    mesh.computeBoundingSphere()
  }

  private updateNodeAppearances(nodeIds: readonly (string | null)[]): void {
    const mesh = this.nodeMesh
    if (!mesh) return
    const indices = new Set<number>()
    for (const nodeId of nodeIds) {
      if (!nodeId) continue
      const index = this.instanceIndexByNodeId.get(nodeId)
      if (index !== undefined) indices.add(index)
    }
    if (indices.size === 0) return

    const matrix = new Matrix4()
    const position = new Vector3()
    const scale = new Vector3()
    mesh.instanceMatrix.clearUpdateRanges()
    mesh.instanceColor?.clearUpdateRanges()
    for (const index of indices) {
      this.writeNodeInstance(mesh, index, matrix, position, scale)
      mesh.instanceMatrix.addUpdateRange(index * 16, 16)
      mesh.instanceColor?.addUpdateRange(index * 3, 3)
    }
    mesh.instanceMatrix.needsUpdate = true
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true
  }

  private writeNodeInstance(
    mesh: InstancedMesh<SphereGeometry, MeshStandardMaterial>,
    index: number,
    matrix: Matrix4,
    position: Vector3,
    scale: Vector3
  ): void {
    const node = this.graph.nodes[index]
    if (!node) return
    const resolved = this.positions.get(node.id)
    if (!resolved) return
    position.set(resolved.x, resolved.y, resolved.z)
    const selected = node.id === this.selectedNodeId
    const hovered = node.id === this.hoveredNodeId
    const nodeScale = (node.size ?? this.nodeSize) * (selected ? 1.48 : hovered ? 1.22 : 1)
    scale.setScalar(nodeScale)
    matrix.compose(position, this.graphGroup.quaternion, scale)
    mesh.setMatrixAt(index, matrix)
    const color = this.baseNodeColors[index]?.clone() ?? new Color(DEFAULT_NODE_COLOR)
    if (selected) color.lerp(new Color(0xffffff), 0.48)
    else if (hovered) color.lerp(new Color(0xffffff), 0.24)
    mesh.setColorAt(index, color)
  }

  private readonly handleContextLost = (event: Event): void => {
    event.preventDefault()
    this.reportError(new Error("知识图 WebGL 上下文已丢失"))
  }

  private readonly handleResize = (): void => {
    if (!this.disposed) this.resize()
  }

  private readonly handlePointerDown = (event: PointerEvent): void => {
    this.pointerDown = { x: event.clientX, y: event.clientY }
  }

  private readonly handlePointerMove = (event: PointerEvent): void => {
    if (this.disposed || this.failed) return
    this.pendingHoverPointer = { x: event.clientX, y: event.clientY }
    if (this.hoverAnimationFrame !== null) return
    this.hoverAnimationFrame = requestAnimationFrame(this.flushHoverPick)
  }

  private readonly flushHoverPick = (): void => {
    this.hoverAnimationFrame = null
    const pointer = this.pendingHoverPointer
    this.pendingHoverPointer = null
    if (this.disposed || !pointer) return
    const node = this.pickNode(pointer.x, pointer.y)
    const nextId = node?.id ?? null
    if (nextId === this.hoveredNodeId) return
    const previousId = this.hoveredNodeId
    this.hoveredNodeId = nextId
    this.renderer.domElement.style.cursor = node ? "pointer" : "grab"
    this.updateNodeAppearances([previousId, nextId])
    this.onNodeHover?.(node)
    this.invalidate()
  }

  private readonly handlePointerUp = (event: PointerEvent): void => {
    const pointerDown = this.pointerDown
    this.pointerDown = null
    if (!pointerDown) return
    if (
      Math.hypot(event.clientX - pointerDown.x, event.clientY - pointerDown.y) >
      POINTER_CLICK_TOLERANCE
    ) {
      return
    }
    const node = this.pickNode(event.clientX, event.clientY)
    const previousId = this.selectedNodeId
    this.selectedNodeId = node?.id ?? null
    this.updateNodeAppearances([previousId, this.selectedNodeId])
    this.onNodePick?.(node)
    this.invalidate()
  }

  private readonly handlePointerLeave = (): void => {
    this.pointerDown = null
    this.pendingHoverPointer = null
    if (this.hoverAnimationFrame !== null) cancelAnimationFrame(this.hoverAnimationFrame)
    this.hoverAnimationFrame = null
    if (this.hoveredNodeId === null) return
    const previousId = this.hoveredNodeId
    this.hoveredNodeId = null
    this.renderer.domElement.style.cursor = "grab"
    this.updateNodeAppearances([previousId])
    this.onNodeHover?.(null)
    this.invalidate()
  }

  private pickNode(clientX: number, clientY: number): VisualizationNode<TNodeData> | null {
    if (!this.nodeMesh || !this.pickingTree) return null
    const bounds = this.renderer.domElement.getBoundingClientRect()
    if (bounds.width <= 0 || bounds.height <= 0) return null
    this.pointer.x = ((clientX - bounds.left) / bounds.width) * 2 - 1
    this.pointer.y = -((clientY - bounds.top) / bounds.height) * 2 + 1
    this.raycaster.setFromCamera(this.pointer, this.camera)

    const stack: PickingTreeNode[] = [this.pickingTree]
    const point = new Vector3()
    let closestIndex = -1
    let closestDistance = Number.POSITIVE_INFINITY
    while (stack.length > 0) {
      const treeNode = stack.pop()
      if (!treeNode || !this.raycaster.ray.intersectsBox(treeNode.bounds)) continue
      if (treeNode.indices) {
        for (const index of treeNode.indices) {
          const node = this.graph.nodes[index]
          const position = node ? this.positions.get(node.id) : undefined
          if (!node || !position) continue
          point.set(position.x, position.y, position.z)
          const radius = (node.size ?? this.nodeSize) * 1.5
          if (this.raycaster.ray.distanceSqToPoint(point) > radius * radius) continue
          const distance = this.raycaster.ray.direction.dot(point.sub(this.raycaster.ray.origin))
          if (
            distance >= this.raycaster.near &&
            distance <= this.raycaster.far &&
            distance < closestDistance
          ) {
            closestDistance = distance
            closestIndex = index
          }
        }
      } else {
        if (treeNode.left) stack.push(treeNode.left)
        if (treeNode.right) stack.push(treeNode.right)
      }
    }
    return closestIndex >= 0 ? (this.nodesByInstanceId[closestIndex] ?? null) : null
  }

  private readonly invalidate = (): void => {
    if (this.disposed || this.failed || this.animationFrame !== null) return
    this.animationFrame = requestAnimationFrame(() => {
      this.animationFrame = null
      if (this.disposed || this.failed) return
      try {
        this.renderer.render(this.scene, this.camera)
      } catch (cause) {
        this.reportError(cause)
      }
    })
  }

  private reportError(cause: unknown): void {
    if (this.disposed || this.failed) return
    this.failed = true
    if (this.hoverAnimationFrame !== null) cancelAnimationFrame(this.hoverAnimationFrame)
    this.hoverAnimationFrame = null
    this.pendingHoverPointer = null
    this.onError?.(toError(cause))
  }

  private disposeGraphResources(): void {
    if (this.nodeMesh) {
      this.graphGroup.remove(this.nodeMesh)
      this.nodeMesh.dispose()
      this.nodeMesh.geometry.dispose()
      this.nodeMesh.material.dispose()
      this.nodeMesh = null
    }
    if (this.edgeLines) {
      this.graphGroup.remove(this.edgeLines)
      this.edgeLines.geometry.dispose()
      this.edgeLines.material.dispose()
      this.edgeLines = null
    }
  }

  private assertActive(): void {
    if (this.disposed) throw new Error("知识可视化引擎已释放")
  }
}

function toColor(color: VisualizationColor): Color {
  return new Color(color)
}

function toError(cause: unknown): Error {
  return cause instanceof Error ? cause : new Error(String(cause))
}
