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
  ConeGeometry,
  DirectionalLight,
  DynamicDrawUsage,
  Group,
  InstancedMesh,
  LineBasicMaterial,
  LineSegments,
  Matrix4,
  MeshBasicMaterial,
  MeshStandardMaterial,
  OrthographicCamera,
  PerspectiveCamera,
  Quaternion,
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
  GraphColorScheme,
  GraphDimension,
  GraphLayout,
  GraphPosition,
  ThreeGraphEngineOptions,
  VisualizationColor,
  VisualizationEdge,
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
const DEFAULT_SPACING = 64
const DEFAULT_LAYER_GAP = 18
const POINTER_CLICK_TOLERANCE = 4
const ORTHOGRAPHIC_VIEW_HEIGHT = 640
// 聚焦节点时，无关节点与关系向背景色混合以弱化；暗色背景下混向深色才会退后
const SCHEME_STYLES = {
  light: {
    dimColor: 0xe2e8f0,
    dimRatio: 0.72,
    focusEdgeColor: 0x0284c7,
    selectedLift: 0x0f172a,
    ambient: 1.15,
    key: 2.2,
    fill: 1.1,
    edgeOpacity: 0.8,
    edgeNeutral: 0xcbd5e1,
    edgeNeutralRatio: 0.5
  },
  dark: {
    dimColor: 0x1e293b,
    dimRatio: 0.82,
    focusEdgeColor: 0x7dd3fc,
    selectedLift: 0xffffff,
    ambient: 1.7,
    key: 1.5,
    fill: 0.7,
    edgeOpacity: 0.7,
    edgeNeutral: 0x334155,
    edgeNeutralRatio: 0.55
  }
} as const
// 超过该节点数不创建 DOM 标签，避免大图拖慢交互
const MAX_LABEL_NODES = 800
const MAX_EDGE_LABELS = 40
const LABEL_HEIGHT = 14
const LABEL_GAP = 4
const LABEL_TEXT_COLOR = "var(--foreground, #0f172a)"
const LABEL_HALO = [2, 4, 6].map((blur) => `0 0 ${blur}px var(--background, #ffffff)`).join(", ")
const UP = new Vector3(0, 1, 0)

interface LabelCandidate {
  readonly index: number
  readonly x: number
  readonly y: number
  readonly priority: number
  readonly dimmed: boolean
}

interface LabelRect {
  readonly left: number
  readonly top: number
  readonly right: number
  readonly bottom: number
}

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
  private readonly scheme: (typeof SCHEME_STYLES)[GraphColorScheme]
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
  private arrowMesh: InstancedMesh<ConeGeometry, MeshBasicMaterial> | null = null
  // 每条关系箭头的完整变换；为 null 表示该关系没有可画的方向（自环或端点重合）
  private arrowMatrices: (Matrix4 | null)[] = []
  private readonly labelLayer: HTMLDivElement
  private nodeLabels: HTMLDivElement[] = []
  private nodeLabelWidths: number[] = []
  private edgeLabels: HTMLDivElement[] = []
  private edgeIndicesByNodeId = new Map<string, number[]>()
  private nodeDegrees: number[] = []
  private focusNodeIds: ReadonlySet<string> | null = null
  private focusEdgeIndices: ReadonlySet<number> | null = null
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
    this.scheme = SCHEME_STYLES[options.colorScheme ?? "light"]
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
    this.labelLayer = createLabelLayer()

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
      this.container.appendChild(this.labelLayer)

      const controls = this.createControls(this.camera)
      this.controls = controls
      this.scene.add(this.graphGroup)
      this.scene.add(new AmbientLight(0xffffff, this.scheme.ambient))
      const keyLight = new DirectionalLight(0xffffff, this.scheme.key)
      keyLight.position.set(120, 180, 260)
      this.scene.add(keyLight)
      const fillLight = new DirectionalLight(0x60a5fa, this.scheme.fill)
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
    this.indexEdges(graph)
    this.createLabels(graph)

    const nodeIds = new Set(graph.nodes.map((node) => node.id))
    if (this.selectedNodeId && !nodeIds.has(this.selectedNodeId)) this.selectedNodeId = null
    if (this.hoveredNodeId && !nodeIds.has(this.hoveredNodeId)) {
      this.hoveredNodeId = null
      this.onNodeHover?.(null)
    }
    this.updateFocusSets()

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
    this.selectedNodeId = nextNodeId
    this.refreshFocus()
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
    if (this.labelLayer.parentElement === this.container) {
      this.container.removeChild(this.labelLayer)
    }
    this.labelLayer.replaceChildren()
    this.nodeLabels = []
    this.nodeLabelWidths = []
    this.edgeLabels = []
    this.edgeIndicesByNodeId.clear()
    this.nodeDegrees = []
    this.focusNodeIds = null
    this.focusEdgeIndices = null
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
        this.arrowMesh = this.createArrowMesh(this.graph)
        if (this.arrowMesh) this.graphGroup.add(this.arrowMesh)
      }
      this.updateNodeInstances()
      this.updateEdgeColors()
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
    // 颜色走 InstancedMesh.instanceColor；球体几何没有 color 顶点属性，开启 vertexColors 会乘上默认的 (0,0,0) 变成黑色
    const material = new MeshStandardMaterial({
      metalness: 0,
      roughness: 0.55
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
      vertices.set([source.x, source.y, source.z, target.x, target.y, target.z], index * 6)
    })

    const geometry = new BufferGeometry()
    geometry.setAttribute("position", new BufferAttribute(vertices, 3))
    geometry.setAttribute("color", new BufferAttribute(colors, 3))
    geometry.computeBoundingSphere()
    const material = new LineBasicMaterial({
      depthWrite: false,
      opacity: this.scheme.edgeOpacity,
      transparent: true,
      vertexColors: true
    })
    return new LineSegments(geometry, material)
  }

  /**
   * 预先计算每条关系终点外侧的箭头变换；箭头默认隐藏，只在聚焦节点时显示其相关关系的方向。
   * 全部箭头共用一个实例化网格。
   */
  private createArrowMesh(
    graph: VisualizationGraph<TNodeData, TEdgeData>
  ): InstancedMesh<ConeGeometry, MeshBasicMaterial> | null {
    if (graph.edges.length === 0) return null
    const geometry = new ConeGeometry(0.5, 1.6, 10)
    const material = new MeshBasicMaterial({ transparent: true, opacity: 0.9 })
    const mesh = new InstancedMesh(geometry, material, graph.edges.length)
    mesh.instanceMatrix.setUsage(DynamicDrawUsage)
    mesh.frustumCulled = false

    const position = new Vector3()
    const direction = new Vector3()
    const rotation = new Quaternion()
    const scale = new Vector3()
    const arrowSize = this.nodeSize * 0.9

    this.arrowMatrices = graph.edges.map((edge) => {
      const source = this.positions.get(edge.source)
      const target = this.positions.get(edge.target)
      if (!source || !target || edge.source === edge.target) return null
      direction.set(target.x - source.x, target.y - source.y, target.z - source.z)
      const length = direction.length()
      if (length < 0.001) return null
      direction.divideScalar(length)
      const targetIndex = this.instanceIndexByNodeId.get(edge.target)
      const targetNode = targetIndex === undefined ? undefined : graph.nodes[targetIndex]
      const targetRadius = targetNode?.size ?? this.nodeSize
      // 箭头尖端贴住终点球面
      const offset = targetRadius + arrowSize * 0.8
      position.set(target.x, target.y, target.z).addScaledVector(direction, -offset)
      rotation.setFromUnitVectors(UP, direction)
      scale.setScalar(arrowSize)
      return new Matrix4().compose(position, rotation, scale)
    })
    return mesh
  }

  /** 按当前聚焦状态重写关系线颜色与箭头：聚焦关系高亮并显示箭头，其余弱化且不显示箭头。 */
  private updateEdgeColors(): void {
    const lines = this.edgeLines
    const arrows = this.arrowMesh
    if (!lines && !arrows) return
    const colorAttribute = lines?.geometry.getAttribute("color")
    const focusEdges = this.focusEdgeIndices
    const dim = new Color(this.scheme.dimColor)
    const highlight = new Color(this.scheme.focusEdgeColor)
    const hidden = new Matrix4().makeScale(0, 0, 0)
    const style = (color: Color, focused: boolean): Color => {
      if (!focusEdges) return color
      return focused ? color.lerp(highlight, 0.6) : color.lerp(dim, this.scheme.dimRatio)
    }

    this.graph.edges.forEach((edge, index) => {
      const focused = focusEdges?.has(index) ?? false
      const [sourceColor, targetColor] = this.edgeEndColors(edge)
      style(sourceColor, focused)
      style(targetColor, focused)
      if (colorAttribute) {
        colorAttribute.setXYZ(index * 2, sourceColor.r, sourceColor.g, sourceColor.b)
        colorAttribute.setXYZ(index * 2 + 1, targetColor.r, targetColor.g, targetColor.b)
      }
      if (arrows) {
        const arrowMatrix = this.arrowMatrices[index]
        arrows.setMatrixAt(index, focused && arrowMatrix ? arrowMatrix : hidden)
        arrows.setColorAt(index, targetColor)
      }
    })
    if (colorAttribute) colorAttribute.needsUpdate = true
    if (arrows) {
      arrows.instanceMatrix.needsUpdate = true
      if (arrows.instanceColor) arrows.instanceColor.needsUpdate = true
    }
  }

  /**
   * 关系两端的基础颜色：调用方指定 color 时两端同色；否则从起点节点色渐变到终点节点色，
   * 并向背景中性色混合，让关系线退到节点之后。
   */
  private edgeEndColors(edge: VisualizationEdge<TEdgeData>): [Color, Color] {
    if (edge.color !== undefined) {
      return [toColor(edge.color), toColor(edge.color)]
    }
    const neutral = new Color(this.scheme.edgeNeutral)
    const endColor = (nodeId: string): Color => {
      const index = this.instanceIndexByNodeId.get(nodeId)
      const base = index === undefined ? undefined : this.baseNodeColors[index]
      return (base?.clone() ?? new Color(DEFAULT_EDGE_COLOR)).lerp(
        neutral,
        this.scheme.edgeNeutralRatio
      )
    }
    return [endColor(edge.source), endColor(edge.target)]
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

  /** 聚焦节点（选中优先，其次悬停）变化后，重算邻域并整体刷新节点、关系与标签。 */
  private refreshFocus(): void {
    this.updateFocusSets()
    this.updateNodeInstances()
    this.updateEdgeColors()
    this.invalidate()
  }

  private updateFocusSets(): void {
    const focusId = this.selectedNodeId ?? this.hoveredNodeId
    if (!focusId || !this.instanceIndexByNodeId.has(focusId)) {
      this.focusNodeIds = null
      this.focusEdgeIndices = null
      return
    }
    const edgeIndices = this.edgeIndicesByNodeId.get(focusId) ?? []
    const nodeIds = new Set<string>([focusId])
    for (const index of edgeIndices) {
      const edge = this.graph.edges[index]
      if (!edge) continue
      nodeIds.add(edge.source)
      nodeIds.add(edge.target)
    }
    this.focusNodeIds = nodeIds
    this.focusEdgeIndices = new Set(edgeIndices)
  }

  private indexEdges(graph: VisualizationGraph<TNodeData, TEdgeData>): void {
    const byNode = new Map<string, number[]>()
    graph.edges.forEach((edge, index) => {
      const endpoints = edge.source === edge.target ? [edge.source] : [edge.source, edge.target]
      for (const nodeId of endpoints) {
        const list = byNode.get(nodeId)
        if (list) list.push(index)
        else byNode.set(nodeId, [index])
      }
    })
    this.edgeIndicesByNodeId = byNode
    this.nodeDegrees = graph.nodes.map((node) => byNode.get(node.id)?.length ?? 0)
  }

  private createLabels(graph: VisualizationGraph<TNodeData, TEdgeData>): void {
    this.labelLayer.replaceChildren()
    this.nodeLabels = []
    this.nodeLabelWidths = []
    this.edgeLabels = []
    if (graph.nodes.length > MAX_LABEL_NODES) return
    this.nodeLabels = graph.nodes.map((node) => {
      const element = createLabelElement(node.label, false)
      this.labelLayer.appendChild(element)
      return element
    })
    this.nodeLabelWidths = graph.nodes.map(() => 0)
  }

  /**
   * 将节点标签投影到屏幕：选中、悬停、邻居和高连接度节点优先放置，与已放置标签重叠的低优先级标签隐藏。
   */
  private updateLabels(): void {
    const width = this.container.clientWidth
    const height = this.container.clientHeight
    if (width <= 0 || height <= 0) return

    const projected = new Vector3()
    const candidates: LabelCandidate[] = []
    const focusNodes = this.focusNodeIds
    const maxDegree = Math.max(1, ...this.nodeDegrees)

    this.nodeLabels.forEach((element, index) => {
      const node = this.graph.nodes[index]
      const position = node ? this.positions.get(node.id) : undefined
      if (!node || !position) {
        hideLabel(element)
        return
      }
      const point = this.projectToScreen(position, projected, width, height)
      if (!point) {
        hideLabel(element)
        return
      }
      const inFocus = focusNodes?.has(node.id) ?? false
      const priority =
        node.id === this.selectedNodeId
          ? 4
          : node.id === this.hoveredNodeId
            ? 3
            : inFocus
              ? 2
              : (this.nodeDegrees[index] ?? 0) / maxDegree
      candidates.push({ index, ...point, priority, dimmed: focusNodes !== null && !inFocus })
    })

    candidates.sort((left, right) => right.priority - left.priority)
    const placed: LabelRect[] = []
    for (const candidate of candidates) {
      const element = this.nodeLabels[candidate.index]
      if (!element) continue
      const labelWidth = this.measureNodeLabel(candidate.index, element)
      const top = candidate.y + this.screenNodeRadius(candidate.index) + LABEL_GAP
      const rect: LabelRect = {
        left: candidate.x - labelWidth / 2,
        top,
        right: candidate.x + labelWidth / 2,
        bottom: top + LABEL_HEIGHT
      }
      // 聚焦相关标签必须显示；聚焦时其他标签隐藏，未聚焦时按优先级互相避让
      const blocked = candidate.dimmed || placed.some((other) => overlaps(rect, other))
      if (candidate.priority < 2 && blocked) {
        hideLabel(element)
        continue
      }
      placed.push(rect)
      // 普通标签半透明退后；聚焦邻居稍亮，选中与悬停节点不透明
      showLabel(element, rect.left, rect.top, labelOpacity(candidate.priority))
      element.style.fontWeight = candidate.priority >= 3 ? "600" : "400"
    }

    this.updateEdgeLabels(projected, width, height)
  }

  /** 只为聚焦节点的关系显示关系名称，避免全图文字堆叠。 */
  private updateEdgeLabels(projected: Vector3, width: number, height: number): void {
    const focusEdges = this.focusEdgeIndices
    const labelled = focusEdges
      ? [...focusEdges].filter((index) => this.graph.edges[index]?.label).slice(0, MAX_EDGE_LABELS)
      : []

    while (this.edgeLabels.length < labelled.length) {
      const element = createLabelElement("", true)
      this.labelLayer.appendChild(element)
      this.edgeLabels.push(element)
    }
    this.edgeLabels.forEach((element, slot) => {
      const edgeIndex = labelled[slot]
      const edge = edgeIndex === undefined ? undefined : this.graph.edges[edgeIndex]
      const source = edge ? this.positions.get(edge.source) : undefined
      const target = edge ? this.positions.get(edge.target) : undefined
      if (!edge?.label || !source || !target) {
        hideLabel(element)
        return
      }
      const point = this.projectToScreen(
        {
          x: (source.x + target.x) / 2,
          y: (source.y + target.y) / 2,
          z: (source.z + target.z) / 2
        },
        projected,
        width,
        height
      )
      if (!point) {
        hideLabel(element)
        return
      }
      if (element.textContent !== edge.label) {
        element.textContent = edge.label
        element.title = edge.label
      }
      const labelWidth = element.offsetWidth
      showLabel(element, point.x - labelWidth / 2, point.y - LABEL_HEIGHT / 2, 0.85)
    })
  }

  private projectToScreen(
    position: GraphPosition,
    projected: Vector3,
    width: number,
    height: number
  ): { readonly x: number; readonly y: number } | null {
    projected.set(position.x, position.y, position.z).project(this.camera)
    if (projected.z < -1 || projected.z > 1) return null
    if (Math.abs(projected.x) > 1.05 || Math.abs(projected.y) > 1.05) return null
    return { x: ((projected.x + 1) / 2) * width, y: ((1 - projected.y) / 2) * height }
  }

  /** 估算节点在屏幕上的半径，使标签紧贴节点下方。 */
  private screenNodeRadius(index: number): number {
    const node = this.graph.nodes[index]
    const radius = node?.size ?? this.nodeSize
    if (this.camera instanceof OrthographicCamera) {
      const height = Math.max(this.container.clientHeight, 1)
      return (radius * this.camera.zoom * height) / ORTHOGRAPHIC_VIEW_HEIGHT
    }
    const position = node ? this.positions.get(node.id) : undefined
    if (!position) return radius
    const distance = this.camera.position.distanceTo(
      new Vector3(position.x, position.y, position.z)
    )
    const height = Math.max(this.container.clientHeight, 1)
    const fov = (this.camera.fov * Math.PI) / 180
    return (radius / (2 * Math.tan(fov / 2) * Math.max(distance, 0.001))) * height
  }

  private measureNodeLabel(index: number, element: HTMLDivElement): number {
    const cached = this.nodeLabelWidths[index] ?? 0
    if (cached > 0) return cached
    const width = element.offsetWidth
    this.nodeLabelWidths[index] = width
    return width
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
    const lift = new Color(this.scheme.selectedLift)
    if (selected) color.lerp(lift, 0.3)
    else if (hovered) color.lerp(lift, 0.15)
    else if (this.focusNodeIds && !this.focusNodeIds.has(node.id)) {
      color.lerp(new Color(this.scheme.dimColor), this.scheme.dimRatio)
    }
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
    this.hoveredNodeId = nextId
    this.renderer.domElement.style.cursor = node ? "pointer" : "grab"
    this.refreshFocus()
    this.onNodeHover?.(node)
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
    this.selectedNodeId = node?.id ?? null
    this.refreshFocus()
    this.onNodePick?.(node)
  }

  private readonly handlePointerLeave = (): void => {
    this.pointerDown = null
    this.pendingHoverPointer = null
    if (this.hoverAnimationFrame !== null) cancelAnimationFrame(this.hoverAnimationFrame)
    this.hoverAnimationFrame = null
    if (this.hoveredNodeId === null) return
    this.hoveredNodeId = null
    this.renderer.domElement.style.cursor = "grab"
    this.refreshFocus()
    this.onNodeHover?.(null)
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
        this.updateLabels()
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
    if (this.arrowMesh) {
      this.graphGroup.remove(this.arrowMesh)
      this.arrowMesh.dispose()
      this.arrowMesh.geometry.dispose()
      this.arrowMesh.material.dispose()
      this.arrowMesh = null
    }
    this.arrowMatrices = []
  }

  private assertActive(): void {
    if (this.disposed) throw new Error("知识可视化引擎已释放")
  }
}

function toColor(color: VisualizationColor): Color {
  return new Color(color)
}

function createLabelLayer(): HTMLDivElement {
  const layer = document.createElement("div")
  layer.setAttribute("aria-hidden", "true")
  Object.assign(layer.style, {
    inset: "0",
    overflow: "hidden",
    pointerEvents: "none",
    position: "absolute"
  })
  return layer
}

function createLabelElement(text: string, edge: boolean): HTMLDivElement {
  const element = document.createElement("div")
  element.textContent = text
  element.title = text
  Object.assign(element.style, {
    color: LABEL_TEXT_COLOR,
    fontSize: edge ? "10px" : "11px",
    left: "0",
    lineHeight: `${LABEL_HEIGHT}px`,
    maxWidth: edge ? "120px" : "160px",
    overflow: "hidden",
    position: "absolute",
    textOverflow: "ellipsis",
    textShadow: LABEL_HALO,
    top: "0",
    visibility: "hidden",
    whiteSpace: "nowrap",
    willChange: "transform"
  })
  if (edge) {
    Object.assign(element.style, {
      background: "var(--background, #ffffff)",
      border: "1px solid var(--border, #e2e8f0)",
      borderRadius: "6px",
      opacity: "0.95",
      padding: "0 4px",
      textShadow: "none"
    })
  }
  return element
}

function showLabel(element: HTMLDivElement, left: number, top: number, opacity: number): void {
  element.style.transform = `translate(${Math.round(left)}px, ${Math.round(top)}px)`
  element.style.opacity = String(opacity)
  element.style.visibility = "visible"
}

function labelOpacity(priority: number): number {
  if (priority >= 3) return 1
  if (priority >= 2) return 0.85
  return 0.65
}

function hideLabel(element: HTMLDivElement): void {
  element.style.visibility = "hidden"
}

function overlaps(left: LabelRect, right: LabelRect): boolean {
  return (
    left.left < right.right &&
    left.right > right.left &&
    left.top < right.bottom &&
    left.bottom > right.top
  )
}

function toError(cause: unknown): Error {
  return cause instanceof Error ? cause : new Error(String(cause))
}
