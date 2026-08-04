/**
 * 知识图的确定性布局计算，保持渲染器与布局算法解耦。
 * @author AaronZZH & Kiro
 */

import type {
  GraphPosition,
  LayoutOptions,
  VisualizationEdge,
  VisualizationGraph,
  VisualizationNode
} from "./types"

const TAU = Math.PI * 2
const DEFAULT_FORCE_ITERATIONS = 72
const LARGE_GRAPH_REPULSION_SAMPLES = 32

/** 校验图标识和关系端点，并返回节点索引。 */
export function validateGraph<TNodeData, TEdgeData>(
  graph: VisualizationGraph<TNodeData, TEdgeData>
): ReadonlyMap<string, number> {
  const nodeIndex = new Map<string, number>()
  const edgeIds = new Set<string>()

  graph.nodes.forEach((node, index) => {
    if (!node.id) throw new Error("知识图节点缺少 id")
    if (nodeIndex.has(node.id)) throw new Error(`知识图存在重复节点 id: ${node.id}`)
    nodeIndex.set(node.id, index)
  })

  for (const edge of graph.edges) {
    if (!edge.id) throw new Error("知识图关系缺少 id")
    if (edgeIds.has(edge.id)) throw new Error(`知识图存在重复关系 id: ${edge.id}`)
    edgeIds.add(edge.id)
    if (!nodeIndex.has(edge.source)) {
      throw new Error(`知识图关系 ${edge.id} 的起点不存在: ${edge.source}`)
    }
    if (!nodeIndex.has(edge.target)) {
      throw new Error(`知识图关系 ${edge.id} 的终点不存在: ${edge.target}`)
    }
  }

  return nodeIndex
}

/** 根据指定维度和布局模式生成稳定节点坐标。 */
export function resolveGraphLayout<TNodeData, TEdgeData>(
  graph: VisualizationGraph<TNodeData, TEdgeData>,
  options: LayoutOptions
): ReadonlyMap<string, GraphPosition> {
  const nodeIndex = validateGraph(graph)
  if (graph.nodes.length === 0) return new Map()

  const positions = createInitialPositions(graph.nodes, options)
  if (options.layout === "force" && graph.nodes.length > 1) {
    relaxPositions(graph.nodes, graph.edges, nodeIndex, positions, options)
  }

  const result = new Map<string, GraphPosition>()
  graph.nodes.forEach((node, index) => {
    const offset = index * 3
    result.set(node.id, {
      x: positions[offset] ?? 0,
      y: positions[offset + 1] ?? 0,
      z: positions[offset + 2] ?? 0
    })
  })
  return result
}

function createInitialPositions<TData>(
  nodes: readonly VisualizationNode<TData>[],
  options: LayoutOptions
): Float64Array {
  const positions = new Float64Array(nodes.length * 3)
  const generated =
    options.layout === "grid"
      ? createGridPositions(nodes.length, options)
      : createRadialPositions(nodes.length, options)

  nodes.forEach((node, index) => {
    const offset = index * 3
    const source = node.position
    positions[offset] = source?.x ?? generated[offset] ?? 0
    positions[offset + 1] = source?.y ?? generated[offset + 1] ?? 0
    positions[offset + 2] =
      options.dimension === "2d"
        ? 0
        : (source?.z ??
          (options.dimension === "2.5d"
            ? (node.layer ?? 0) * options.layerGap
            : (generated[offset + 2] ?? 0)))
  })

  return positions
}

function createGridPositions(count: number, options: LayoutOptions): Float64Array {
  const positions = new Float64Array(count * 3)
  if (options.dimension === "3d") {
    const side = Math.max(1, Math.ceil(Math.cbrt(count)))
    const center = (side - 1) / 2
    for (let index = 0; index < count; index += 1) {
      const x = index % side
      const y = Math.floor(index / side) % side
      const z = Math.floor(index / (side * side))
      positions[index * 3] = (x - center) * options.spacing
      positions[index * 3 + 1] = (center - y) * options.spacing
      positions[index * 3 + 2] = (z - center) * options.spacing
    }
    return positions
  }

  const columns = Math.max(1, Math.ceil(Math.sqrt(count)))
  const rows = Math.ceil(count / columns)
  for (let index = 0; index < count; index += 1) {
    positions[index * 3] = ((index % columns) - (columns - 1) / 2) * options.spacing
    positions[index * 3 + 1] = ((rows - 1) / 2 - Math.floor(index / columns)) * options.spacing
  }
  return positions
}

function createRadialPositions(count: number, options: LayoutOptions): Float64Array {
  const positions = new Float64Array(count * 3)
  const radius = Math.max(options.spacing, Math.sqrt(count) * options.spacing * 0.72)

  for (let index = 0; index < count; index += 1) {
    if (options.dimension === "3d") {
      const ratio = (index + 0.5) / count
      const y = 1 - ratio * 2
      const ringRadius = Math.sqrt(Math.max(0, 1 - y * y))
      const angle = Math.PI * (3 - Math.sqrt(5)) * index
      positions[index * 3] = Math.cos(angle) * ringRadius * radius
      positions[index * 3 + 1] = y * radius
      positions[index * 3 + 2] = Math.sin(angle) * ringRadius * radius
    } else {
      const angle = (index / count) * TAU
      positions[index * 3] = Math.cos(angle) * radius
      positions[index * 3 + 1] = Math.sin(angle) * radius
    }
  }
  return positions
}

function relaxPositions<TNodeData, TEdgeData>(
  nodes: readonly VisualizationNode<TNodeData>[],
  edges: readonly VisualizationEdge<TEdgeData>[],
  nodeIndex: ReadonlyMap<string, number>,
  positions: Float64Array,
  options: LayoutOptions
): void {
  const count = nodes.length
  const forces = new Float64Array(count * 3)
  const fixed = nodes.map((node) => node.position !== undefined)
  const iterations = options.iterations ?? DEFAULT_FORCE_ITERATIONS
  const dimensions = options.dimension === "3d" ? 3 : 2
  const idealDistance = options.spacing
  const repulsion = idealDistance * idealDistance * 0.85

  for (let iteration = 0; iteration < iterations; iteration += 1) {
    forces.fill(0)
    applyRepulsion(positions, forces, count, dimensions, repulsion, iteration)
    applyAttraction(edges, nodeIndex, positions, forces, idealDistance, dimensions)
    applyGravity(nodes, positions, forces, options)

    const cooling = 1 - iteration / iterations
    const maxStep = Math.max(0.35, idealDistance * 0.1 * cooling)
    for (let index = 0; index < count; index += 1) {
      if (fixed[index]) continue
      const offset = index * 3
      for (let axis = 0; axis < dimensions; axis += 1) {
        const delta = Math.max(-maxStep, Math.min(maxStep, forces[offset + axis] ?? 0))
        positions[offset + axis] = (positions[offset + axis] ?? 0) + delta
      }
      if (options.dimension === "2d") positions[offset + 2] = 0
    }
  }
}

function applyRepulsion(
  positions: Float64Array,
  forces: Float64Array,
  count: number,
  dimensions: number,
  repulsion: number,
  iteration: number
): void {
  const compareAll = count <= 240
  const samples = compareAll ? count - 1 : Math.min(LARGE_GRAPH_REPULSION_SAMPLES, count - 1)

  for (let source = 0; source < count; source += 1) {
    for (let sample = 0; sample < samples; sample += 1) {
      const target = compareAll
        ? sample >= source
          ? sample + 1
          : sample
        : (source * 31 + sample * 17 + iteration * 13 + 1) % count
      if (source === target) continue

      const sourceOffset = source * 3
      const targetOffset = target * 3
      let distanceSquared = 0
      const deltas = [0, 0, 0]
      for (let axis = 0; axis < dimensions; axis += 1) {
        const delta = (positions[sourceOffset + axis] ?? 0) - (positions[targetOffset + axis] ?? 0)
        deltas[axis] = delta
        distanceSquared += delta * delta
      }
      distanceSquared = Math.max(distanceSquared, 0.01)
      const strength = repulsion / distanceSquared / (compareAll ? 1 : samples / count)
      for (let axis = 0; axis < dimensions; axis += 1) {
        forces[sourceOffset + axis] =
          (forces[sourceOffset + axis] ?? 0) + (deltas[axis] ?? 0) * strength * 0.01
      }
    }
  }
}

function applyAttraction<TData>(
  edges: readonly VisualizationEdge<TData>[],
  nodeIndex: ReadonlyMap<string, number>,
  positions: Float64Array,
  forces: Float64Array,
  idealDistance: number,
  dimensions: number
): void {
  for (const edge of edges) {
    const source = nodeIndex.get(edge.source)
    const target = nodeIndex.get(edge.target)
    if (source === undefined || target === undefined) continue
    const sourceOffset = source * 3
    const targetOffset = target * 3
    let distanceSquared = 0
    const deltas = [0, 0, 0]
    for (let axis = 0; axis < dimensions; axis += 1) {
      const delta = (positions[targetOffset + axis] ?? 0) - (positions[sourceOffset + axis] ?? 0)
      deltas[axis] = delta
      distanceSquared += delta * delta
    }
    const distance = Math.max(Math.sqrt(distanceSquared), 0.01)
    const strength = ((distance - idealDistance) / distance) * 0.035 * (edge.strength ?? 1)
    for (let axis = 0; axis < dimensions; axis += 1) {
      const force = (deltas[axis] ?? 0) * strength
      forces[sourceOffset + axis] = (forces[sourceOffset + axis] ?? 0) + force
      forces[targetOffset + axis] = (forces[targetOffset + axis] ?? 0) - force
    }
  }
}

function applyGravity<TData>(
  nodes: readonly VisualizationNode<TData>[],
  positions: Float64Array,
  forces: Float64Array,
  options: LayoutOptions
): void {
  nodes.forEach((node, index) => {
    const offset = index * 3
    forces[offset] = (forces[offset] ?? 0) - (positions[offset] ?? 0) * 0.003
    forces[offset + 1] = (forces[offset + 1] ?? 0) - (positions[offset + 1] ?? 0) * 0.003
    if (options.dimension === "3d") {
      forces[offset + 2] = (forces[offset + 2] ?? 0) - (positions[offset + 2] ?? 0) * 0.003
    } else if (options.dimension === "2.5d") {
      const targetZ = (node.layer ?? 0) * options.layerGap
      forces[offset + 2] = (targetZ - (positions[offset + 2] ?? 0)) * 0.08
    }
  })
}
