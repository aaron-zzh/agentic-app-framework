---
level: Practice
layer: Product
purpose: DataProcess 数据处理分析引擎设计——结构化/半结构化数据的批/流处理与统计分析
status: draft
version: 1.0.0
date: 2026-05-08
author: AaronZZH
changelog:
  - 2026-05-08 v1.0.0 | 初稿占位，规划 v2.0 迁移到 actormesh
---

# DataProcess 数据处理分析引擎

> **引擎层（Layer 2）的通用执行能力**，负责结构化/半结构化数据的批/流处理与统计分析。
> 本文档为占位，详细设计待补充。
> 长期规划：**v2.0 用 [actormesh](../../../../../apps/actormesh/Readme.md) 实现**，获得 C++ 级高并发性能。

## 定位

**"数据处理分析"**：结构化/半结构化数据的采集、清洗、转换、聚合、计算、可视化前置处理。与 SemanticCalcEngine（语义计算）互补：

| 引擎 | 处理对象 | 核心输入 | 核心输出 |
|------|---------|---------|---------|
| SemanticCalcEngine | 自然语言 / 非结构化语义 | 文本 | 向量 / 实体 / 意图 |
| **DataProcessEngine** | 结构化 / 半结构化数据 | 表格 / JSON / 时序 / 事件流 | 统计 / 聚合 / 转换结果 |

| 层 | 角色 |
|----|------|
| **DataProcessEngine（本文档）** | 通用数据处理与计算能力 |
| **业务使用方** | 报表服务、仪表盘、BI、日志分析、指标计算、工作流节点、Agent 数据工具 |

## 为什么独立为引擎

数据处理是**跨业务横切能力**，不属于某个具体业务模块：

- 报表/仪表盘需要聚合计算
- 指标体系需要多维度统计
- Agent 工具需要数据查询与转换
- 日志分析需要流式处理
- 用户行为分析需要时序聚合
- Learning 反哺需要统计评估

如果各自实现，性能优化难统一、计算逻辑散落、资源调度不集中。独立成引擎后，计算能力集中管控。

## 核心能力（占位清单）

### 批处理

| 能力 | 说明 |
|------|------|
| 数据读取 | 多源读取（DB / 文件 / API / 对象存储） |
| 数据清洗 | 去重 / 缺失值处理 / 格式校验 / 异常过滤 |
| 数据转换 | 字段映射 / 类型转换 / 计算列 / 拆分合并 |
| 聚合统计 | 分组聚合 / 多维透视 / 累计计算 |
| 关联计算 | Join / 查找 / 合并 |
| 排序分页 | 大数据集排序 + 分页 |
| 批量导出 | 结果写回 DB / Excel / CSV / JSON |

### 流处理

| 能力 | 说明 |
|------|------|
| 事件流接入 | Kafka / Redis Stream / 内部 ApplicationEvent |
| 窗口聚合 | 滑动 / 滚动 / 会话窗口 |
| 实时过滤 | 条件过滤 / 异常检测 |
| 实时 Join | 流与流 Join / 流与表 Join |
| 状态管理 | 有状态计算、checkpoint |

### 分析

| 能力 | 说明 |
|------|------|
| 统计指标 | sum/avg/min/max/count/distinct/percentile |
| 时序分析 | 同比 / 环比 / 趋势 / 季节性 |
| 分组透视 | 多维交叉分析 |
| 异常检测 | 基于阈值 / 基于统计 / 基于 ML（可对接 SemanticCalcEngine） |
| 数据画像 | 自动字段统计（分布、缺失、类型） |

### 可视化前置

| 能力 | 说明 |
|------|------|
| 图表数据准备 | 按图表类型预聚合（折线/柱状/饼/热力/桑基） |
| 大数据集抽样 | 下采样、分位抽样 |
| 差分编码 | 增量数据推送 |

## 接口草案

```java
public interface DataProcessEngine {
    // 批处理
    Dataset<?> read(ReadSource source);
    Dataset<?> transform(Dataset<?> input, TransformPipeline pipeline);
    AggregationResult aggregate(Dataset<?> input, AggregationSpec spec);
    void write(Dataset<?> data, WriteTarget target);

    // 流处理
    StreamSession start(StreamSource source, StreamPipeline pipeline);
    void stop(String sessionId);

    // 分析
    StatisticsReport statistics(Dataset<?> input, StatisticsSpec spec);
    TimeSeriesReport timeSeries(Dataset<?> input, TimeSeriesSpec spec);
    AnomalyReport detectAnomaly(Dataset<?> input, AnomalySpec spec);

    // 图表数据
    ChartData prepareChart(Dataset<?> input, ChartType type, ChartConfig config);
}
```

## 实现策略

### v0.x：Java 基础实现

基于 JDK 25 + 虚拟线程 + Stream API：

- **批处理**：Stream + 虚拟线程并行处理
- **流处理**：Spring ApplicationEvent + 内部事件总线；外部流先不引 Kafka
- **聚合**：JDBC 原生 SQL（PostgreSQL 窗口函数/聚合函数）+ 内存计算结合
- **可视化前置**：纯 Java 数据整形

### v2.0：迁移到 actormesh

**为什么迁移**：

| 痛点 | Java 实现 | actormesh 实现 |
|------|----------|---------------|
| 百万级数据 CPU 密集计算 | GC 压力、内存抖动 | C++ 原生性能 |
| 万级并发流处理 | 线程开销、上下文切换 | Actor 模型、零拷贝 |
| 大数据集内存占用 | JVM 堆限制、对象头膨胀 | 精细内存管理 |
| 高频聚合计算 | JIT 优化有限 | SIMD / 向量化 |

**迁移策略**：

1. v0.x 通过 Spring 接口抽象（SPI）定义 `DataProcessEngine`
2. v1.0 Java 实现稳定后，保持接口不变
3. v2.0 替换为 actormesh 实现（通过 JNI / gRPC 桥接）
4. 业务代码不感知底层切换

### 缓存与预计算

- 聚合结果缓存（相同查询参数命中）
- 物化视图（高频报表预计算）
- 分区裁剪（时间/维度）

## 与其他引擎的协作

| 引擎 | 协作方式 |
|------|---------|
| SemanticCalcEngine | 异常检测可调用其 ML 能力；文本字段可调用其分类 |
| NexusKBEngine | 知识图谱数据的聚合分析 |
| AtomMemoryEngine | 行为记忆的时序统计（Learning 反哺评估） |
| 文档引擎 | 文档使用数据分析（阅读量/版本演化） |
| 监控 | 指标计算的一部分可交由本引擎 |
| 工作流引擎 | 作为工作流节点执行数据处理步骤 |

## 使用场景举例

| 场景 | 能力 |
|------|------|
| 管理后台报表 | 批处理 + 聚合 + 图表数据准备 |
| 实时监控仪表盘 | 流处理 + 窗口聚合 + 差分编码推送 |
| Agent 数据分析工具 | 批处理 + 统计指标 + 异常检测 |
| Learning 效果评估 | 时序分析 + 同比环比 + 分组透视 |
| 用户行为分析 | 流接入 + 会话窗口 + 多维透视 |
| 计费对账 | 批处理 + 关联计算 + 异常检测 |

## 与业界框架的对照

| 框架 | 借鉴点 | 不直接引入的原因 |
|------|--------|----------------|
| Apache Spark | 批处理 API 设计、RDD/DataFrame 模型 | 集群依赖重，v0.x 单体不需要 |
| Apache Flink | 流处理 API、窗口语义、状态管理 | 同上 |
| Polars / DuckDB | 列式/向量化计算思想 | v2.0 actormesh 可借鉴其内存模型 |
| Pandas | DataFrame API 设计 | Python 生态，不直接用 |

## 非目标

- 不做数据存储——那是 PostgreSQL / 对象存储的职责
- 不做 ETL 调度编排——那是工作流引擎的职责（本引擎作为节点被调用）
- 不做机器学习训练——那是 SemanticCalcEngine + 外部模型服务的职责
- 不做可视化渲染——那是前端（webui）的职责，本引擎只准备数据

## 后续补全

- [ ] 各能力的详细接口契约
- [ ] Dataset 抽象设计（列式 vs 行式）
- [ ] TransformPipeline DSL 设计
- [ ] 流处理状态管理与 checkpoint
- [ ] v2.0 actormesh 迁移的 SPI 设计
- [ ] 性能基准（Java vs actormesh）

## 相关文档

- [actormesh 引擎开发框架](../../../../../apps/actormesh/Readme.md)
- [SemanticCalc 语义计算引擎](semantic-compute.md)
- [Cognition 认知层设计](../../intelligent/cognition/cognition.md)
- [路线图 - v2.0 actormesh](../../../../prd/roadmap.md)
