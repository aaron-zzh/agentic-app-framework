---
level: Practice
layer: Product
purpose: PhysicsSpaceTime 物理时空引擎设计——世界模型、时空坐标、物质与物理规则
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v1.0.0 | 初稿占位
---

# PhysicsSpaceTime 物理时空引擎

> **引擎层（Layer 2）的通用执行能力**，为虚拟空间、3D 场景、语义聚合等场景提供统一的时空坐标与物理规则。
> 本文档为占位，详细设计待补充。
> 长期规划：**v2.0 高密度物理计算迁移到 [actormesh](../../../../apps/actormesh/Readme.md)**，获得 C++ 级性能。

## 定位

**"物理时空"**：为 AAF 的制品（文档、知识、记忆、Agent、用户）提供**统一的空间坐标 + 时间维度 + 物质属性 + 物理规则**，让原本抽象的"信息对象"具备可视化、可聚合、可演化的物理形态。

| 层 | 角色 |
|----|------|
| **PhysicsSpaceTimeEngine（本文档）** | 时空与物理计算通用能力 |
| **业务使用方** | 虚拟空间、3D 场景、知识图谱布局、聚类可视化、时间线、Agent 空间化协作 |

## 为什么独立为引擎

在 AAF 架构思想中：

- **一切皆文档**：文档即物质，有坐标/体积/质量
- **世界模型**：坐标系、空间层级、时间维度
- **物理规则**：运动、碰撞、引力聚合、语义相似度驱动聚合

这些能力横切多个场景：虚拟空间的 3D 视图、知识图谱的力导向布局、记忆的时间线、多 Agent 协作的空间分布、众包任务的"引力聚类"——如果各自实现，会重复造轮子且规则不统一。独立为引擎后，时空语义一致。

## 核心概念

### 世界模型

```
World {
    coordinateSystem    # 坐标系（笛卡尔 / 极坐标 / 层级 / 语义空间）
    dimensions          # 维度（2D / 3D / N 维语义空间）
    timeAxis            # 时间轴（绝对时间 / 相对时间 / 语义时间）
    physicsConfig       # 物理规则配置
}
```

### 物质（Matter）

文档、知识节点、记忆原子、Agent 实例都是"物质"：

```
Matter {
    id
    position            # 坐标（空间位置）
    velocity            # 速度（移动轨迹）
    mass                # 质量（语义权重 / 重要度 / 被引用次数）
    volume              # 体积（信息密度 / 内容长度）
    semanticVector      # 语义向量（决定语义空间中的位置）
    lifecycle           # 生命周期时间段
}
```

### 物理规则

| 规则 | 说明 | 应用 |
|------|------|------|
| 运动 | 物质按速度/力在空间中移动 | 动态可视化、轨迹回放 |
| 碰撞 | 两物质位置接近触发事件 | 相似内容合并提示、文档冲突检测 |
| **语义引力** | 语义相似度产生"引力"，相近物质聚合 | 知识图谱聚类、相似文档自动分组 |
| 排斥 | 冲突/矛盾内容相互排斥 | 观点对立可视化、规范冲突定位 |
| 重力场 | 高权重物质产生吸引场 | 热门知识/核心规范作为引力中心 |
| 时间流 | 物质随时间演化（新鲜度衰减） | 过时知识自动下沉、新知识上浮 |

## 核心能力（占位清单）

### 时空计算

| 能力 | 说明 |
|------|------|
| 坐标转换 | 笛卡尔 ↔ 极坐标 ↔ 语义空间 ↔ 层级空间 |
| 距离计算 | 欧氏 / 曼哈顿 / 余弦 / 测地 / 语义距离 |
| 邻近查询 | KNN / 范围查询 / 空间索引（R-Tree / KD-Tree） |
| 时态回溯 | 给定时间点的世界快照（借鉴双时态模型） |
| 轨迹记录 | 物质在时空中的移动轨迹 |

### 物理仿真

| 能力 | 说明 |
|------|------|
| 力场计算 | 引力/排斥/扩散等合力 |
| 运动积分 | Verlet / Runge-Kutta 数值积分 |
| 碰撞检测 | Broad-phase + Narrow-phase 两阶段 |
| 稳定性控制 | 能量衰减、速度阻尼 |
| 并行计算 | Barnes-Hut 树（v0.x Java 实现）/ 全 N-body（v2.0 actormesh） |

### 语义聚合

| 能力 | 说明 |
|------|------|
| 语义引力聚类 | 基于向量相似度的软聚类，不预设簇数 |
| 层级聚合 | 大范围 → 中聚类 → 小簇 的层级结构 |
| 边界检测 | 簇之间的过渡地带识别 |
| 聚类漂移 | 新物质加入导致的聚类重构 |

### 可视化前置

| 能力 | 说明 |
|------|------|
| 3D 坐标导出 | 输出给前端 Three.js / Babylon.js 渲染 |
| 力导向布局 | 类似 D3 force-directed graph，适配知识图谱 |
| 时间线生成 | 沿时间轴分布的物质序列 |
| LOD 降采样 | 大规模场景按视距降采样 |

## 接口草案

```java
public interface PhysicsSpaceTimeEngine {
    // 世界管理
    World createWorld(WorldConfig config);
    void destroy(String worldId);

    // 物质操作
    void addMatter(String worldId, Matter matter);
    void updateMatter(String worldId, String matterId, MatterUpdate update);
    void removeMatter(String worldId, String matterId);

    // 查询
    List<Matter> neighbors(String worldId, Vector position, double radius);
    List<Matter> knn(String worldId, Vector position, int k);
    WorldSnapshot snapshot(String worldId, Instant at);

    // 仿真
    void tick(String worldId, double deltaTime);      // 单步仿真
    void simulate(String worldId, long steps);        // 批量仿真

    // 聚类与布局
    List<Cluster> semanticClusters(String worldId, ClusterConfig config);
    LayoutResult layout(String worldId, LayoutAlgorithm algorithm);

    // 可视化数据
    SceneData exportScene(String worldId, ExportConfig config);
}
```

## 实现策略

### v0.x：Java 基础实现

- **空间索引**：JTS（2D）/ 自研 KD-Tree（3D/N 维）
- **向量计算**：JDK 25 Vector API（SIMD）+ Commons Math
- **物理仿真**：简化 N-body，Barnes-Hut 树近似
- **并行计算**：虚拟线程 + Structured Concurrency
- **持久化**：物质状态存 PostgreSQL + PgVector，轨迹存时序索引

### v2.0：迁移到 actormesh

**为什么迁移**：

| 痛点 | Java 实现 | actormesh 实现 |
|------|----------|---------------|
| 万级以上物质实时仿真 | GC 停顿、内存抖动 | C++ 原生、零 GC |
| 高频碰撞检测 | JIT 优化有限 | SIMD 向量化 |
| 大规模力场计算 | 线程开销 | Actor 并发 + 无锁 |
| 3D 场景渲染前置 | 内存拷贝开销 | 零拷贝数据交换 |

**迁移策略**：通过 SPI 抽象 `PhysicsSpaceTimeEngine` 接口，v2.0 替换为 actormesh 实现（JNI/gRPC 桥接），业务无感知。

## 与其他引擎的协作

| 引擎 | 协作方式 |
|------|---------|
| NexusKBEngine | 知识节点作为"物质"，关系提供力场约束 |
| AtomMemoryEngine | 记忆原子作为"物质"，按时间轴分布，基于价值权重产生引力 |
| SemanticCalcEngine | 提供语义向量，决定物质在语义空间的位置 |
| DataProcessEngine | 大规模物质状态的统计与聚合 |
| 文档引擎 | 文档作为"物质"，版本演化产生时间流 |
| 语义组件引擎 | 3D 场景数据的渲染前置 |

## 使用场景举例

| 场景 | 能力 |
|------|------|
| 知识图谱 3D 可视化 | 物质（节点）+ 力导向布局 + 语义引力聚类 |
| 虚拟空间协作 | 多 Agent 在共享世界中的空间化协作 |
| 记忆时间线 | 记忆原子沿时间轴分布 + 价值权重产生热力 |
| 相似内容自动聚合 | 语义引力驱动，新内容自动"落到"相关簇 |
| 版本演化可视化 | 文档不同版本作为轨迹点，时间流回放 |
| 众包任务引力分布 | 任务按难度/价值聚合，用户按能力靠近 |
| 规范冲突检测 | 互斥规范产生排斥力，空间距离可视化冲突严重度 |

## 与业界框架的对照

| 框架 | 借鉴点 | 不引入原因 |
|------|--------|-----------|
| D3 force | 力导向布局思想 | 前端 JS，后端需 Java 重写 |
| Cytoscape | 大规模图布局算法 | Java 版本维护一般 |
| Box2D / Bullet | 物理仿真算法 | 游戏导向，本场景需语义化改造 |
| NASA WorldWind | 地理空间坐标系 | 过重，按需借鉴 |

## 非目标

- 不做 3D 渲染——那是前端（Three.js / Babylon.js）的职责
- 不做物理材质/光照——业务范畴，超出引擎定位
- 不做地理信息系统（GIS）——专业 GIS 另行处理
- 不做实时游戏物理——焦点在语义物理与可视化布局

## 后续补全

- [ ] 坐标系与向量运算的详细接口
- [ ] 语义引力的公式设计（距离/权重/衰减）
- [ ] 空间索引的性能基准
- [ ] 时态回溯的实现策略
- [ ] actormesh 迁移的 SPI 契约
- [ ] 前端渲染协议（SceneData 规范）

## 相关文档

- [actormesh 引擎开发框架](../../../../apps/actormesh/Readme.md)
- [NexusKB 连接式知识引擎](../data-knowledge/nexus-knowledge.md)
- [AtomMemory 原子记忆引擎](../data-knowledge/atom-memory.md)
- [SemanticCalc 语义计算引擎](../data-knowledge/semantic-compute.md)
- [DataProcess 数据处理引擎](../data-knowledge/data-process-engine.md)
- [元引擎设计](../meta/meta-engine.md)
