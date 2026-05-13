---
level: Practice
layer: Product
purpose: 前端 WebAssembly（C++）性能层设计方向
status: draft
version: 1.0.0
date: 2026-05-10
author: AaronZZH
---

# 前端 WebAssembly 性能层（C++）

> 本文档为方向性设计，具体实现在遇到实测性能瓶颈时再展开

## 一、动机

AAF 前端涉及复杂交互场景（工作流编排、知识图谱可视化、文档协同、AI 多 Agent 状态管理），部分计算密集型算法放在前端执行可减少服务端压力、降低延迟、支持离线场景。选择 C++ 编译为 WebAssembly 作为性能层。

## 二、候选场景

| 场景 | 计算特征 | 优先级 | 备注 |
|------|---------|--------|------|
| 图布局算法（力导向/层次/树形） | CPU 密集、大量浮点运算 | P1 | 工作流/知识图谱节点数多时 JS 卡顿 |
| CRDT 大文档合并 | 内存密集、二进制操作 | P2 | 万级 update 合并场景 |
| 客户端向量相似度计算 | SIMD 友好、批量浮点 | P2 | 本地语义搜索/推荐 |
| 文档 Diff 算法 | 字符串密集操作 | P3 | 大文档版本对比 |
| DSL 解析/校验 | 递归下降/状态机 | P3 | Magic-DSL 前端实时校验 |
| 数据聚合/统计 | 数值计算 | P3 | 仪表板大数据集客户端聚合 |
| 图像处理（缩略图/滤镜） | 像素级操作 | P4 | 减少服务端图片处理压力 |

## 三、技术方案

### 3.1 工具链

```text
C++ 源码 → Emscripten (emcc) → .wasm + JS glue
                                    ↓
                          前端通过 ES Module 加载
```

- 编译器：Emscripten（C/C++ → WASM 的标准工具链）
- 构建集成：CMake + Emscripten，通过 Nx target 桥接
- 包格式：编译产物作为 `packages/wasm/` 共享包发布

### 3.2 前端集成模式

```typescript
// 异步加载 WASM 模块（避免阻塞主线程）
const wasm = await import('@aaf/wasm');

// 或在 Web Worker 中运行（推荐 CPU 密集场景）
const worker = new Worker(new URL('./layout-worker.ts', import.meta.url));
worker.postMessage({ nodes, edges, algorithm: 'force-directed' });
```

### 3.3 目录结构（预留）

```text
packages/
└── wasm/
    ├── src/                → C++ 源码
    │   ├── graph-layout/   → 图布局算法
    │   ├── crdt/           → CRDT 操作加速
    │   ├── vector/         → 向量计算
    │   └── common/         → 共享工具（内存管理/类型转换）
    ├── include/            → 头文件
    ├── CMakeLists.txt      → CMake 构建配置
    ├── package.json        → Nx 包定义
    └── index.ts            → TypeScript binding（类型安全封装）
```

### 3.4 JS ↔ WASM 数据传递

- 简单类型（数字/布尔）：零成本传递
- 数组/Buffer：通过 SharedArrayBuffer 或 WASM 线性内存直接访问，避免拷贝
- 复杂对象：序列化为 JSON 或自定义二进制格式传入

### 3.5 性能优化手段

- WASM SIMD：向量/矩阵运算使用 128-bit SIMD 指令
- WASM Threads：多线程并行（需 SharedArrayBuffer + COOP/COEP headers）
- 内存池：预分配内存避免频繁 malloc/free
- Web Worker：WASM 计算放 Worker 线程，不阻塞 UI

## 四、引入时机

```text
v0.1-v1.0：纯 JS/TS 实现所有功能
    ↓
性能 Profiling 发现瓶颈（Chrome DevTools / Lighthouse）
    ↓
评估：Web Worker + 算法优化能否解决？
    ↓ 不能
v2.0+：针对具体瓶颈模块引入 C++ WASM
```

**原则**：先证明瓶颈存在，再引入 WASM。不做预优化。

## 五、参考

- AFFiNE：Rust → NAPI-RS（Electron native addon），非 WASM，但思路类似——将性能敏感操作下沉到原生层
- Figma：C++ → WASM，渲染引擎和布局算法全部在 WASM 中运行
- Google Earth：C++ → WASM，3D 渲染和地理计算
- AutoCAD Web：C++ → WASM，CAD 引擎移植

## 六、风险与约束

| 风险 | 缓解措施 |
|------|---------|
| WASM 包体积大 | 按模块拆分、懒加载、Tree-shaking 无用导出 |
| 调试困难 | 编译时开启 DWARF source map（`-g`），Chrome 支持 WASM 调试 |
| 浏览器兼容性 | WASM 基础支持 >97%；SIMD/Threads 需 fallback |
| JS ↔ WASM 通信开销 | 批量传递、减少跨边界调用次数、用 SharedArrayBuffer |
| 团队 C++ 能力 | 限定模块边界，接口用 TypeScript 封装，C++ 仅实现核心算法 |
