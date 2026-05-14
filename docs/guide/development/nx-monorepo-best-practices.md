---
level: Practice
layer: Product
purpose: AAF Nx monorepo 工程化最佳实践，指导日常开发与后续优化
status: draft
version: 1.0.0
date: 2026-05-14
author: AaronZZH
gains:
  - 掌握 AAF 项目中 Nx 的核心配置与使用方式
  - 知道如何利用缓存、并行、affected 提升开发效率
  - 了解后续优化方向和演进路径
---

# Nx Monorepo 最佳实践

> AAF 使用 Nx 22 + pnpm 11 管理 Java 后端 + Next.js 前端 + 共享包的单体仓库。
> 本文档是日常开发的操作参考，也是后续工程化优化的基线。

## 一、项目结构与 Nx 项目映射

```text
apps/
├── service/     → Nx 项目 "service"（Maven 多模块，通过 project.json 桥接）
├── webui/       → Nx 项目 "webui"（Next.js App Router）
└── uniapp/      → 待开发
packages/        → 共享库（v0.2+ 落地，每个包一个 Nx 项目）
```

核心思路：**Maven 命令通过 `project.json` 桥接为 Nx target**，前后端统一用 `pnpm nx <target> <project>` 执行，享受缓存和依赖编排。

## 二、统一命令体系

### 2.1 日常开发

```bash
# 启动后端
pnpm nx serve service

# 启动前端
pnpm nx dev webui

# 单项目测试（快速迭代）
pnpm nx test service        # Java 单测（Surefire）
pnpm nx test webui          # TS 单测（Vitest）

# 单项目类型检查
pnpm nx typecheck webui     # tsgo --noEmit

# 单项目构建
pnpm nx build service       # mvnw package
pnpm nx build webui         # next build
```

### 2.2 完工验证

```bash
# 只验证受影响的项目（完工前必跑）
pnpm check:affected         # = nx affected -t check

# 全量验证
pnpm check                  # = nx run-many -t check

# 验收测试
pnpm acceptance:affected    # = nx affected -t acceptance
```

### 2.3 格式化

```bash
pnpm format                 # nx format:write（Prettier）
pnpm format:check           # nx format:check
pnpm nx format service      # Java Spotless apply
pnpm nx lint service        # Java Spotless check
```

### 2.4 Target 依赖关系

```text
check（noop）
├── lint        → 代码风格检查
├── test        → 单元测试
└── build       → 编译/构建
    └── typecheck（webui）→ 类型检查
```

`check` 是复合 target，通过 `dependsOn` 串联子任务。执行 `check` 会自动按依赖顺序执行所有子任务。

## 三、缓存策略

### 3.1 当前配置（nx.json）

```json
{
  "namedInputs": {
    "default": ["{projectRoot}/**/*", "sharedGlobals"],
    "production": ["default"],
    "sharedGlobals": []
  }
}
```

### 3.2 推荐优化（待实施）

精细化 `namedInputs`，避免改文档/测试触发无关重建：

```json
{
  "namedInputs": {
    "default": [
      "{projectRoot}/**/*",
      "!{projectRoot}/node_modules/**/*",
      "!{projectRoot}/**/*.md",
      "!{projectRoot}/**/*.yml",
      "!{projectRoot}/**/*.yaml"
    ],
    "production": [
      "default",
      "!{projectRoot}/**/*.test.*",
      "!{projectRoot}/**/*.spec.*",
      "!{projectRoot}/**/__tests__/**",
      "!{projectRoot}/**/*.accept.test.*"
    ],
    "test": [
      "{projectRoot}/src/**/*.ts",
      "{projectRoot}/src/**/*.tsx",
      "{projectRoot}/src/**/*.java",
      "{projectRoot}/**/__tests__/**",
      "{projectRoot}/**/*.test.*",
      "{projectRoot}/**/*.spec.*"
    ]
  }
}
```

**效果**：
- 改 README/CHANGELOG 不触发 build/test
- 改测试文件不触发 production build
- test target 只关注源码和测试文件

### 3.3 各 Target 的 inputs/outputs

| 项目 | Target | inputs | outputs |
|------|--------|--------|---------|
| service | build | `src/**/*`, `pom.xml` | `target/` |
| service | test | `src/**/*`, `pom.xml` | `target/surefire-reports` |
| webui | build | `src/**/*`, `next.config.js`, `tsconfig.json` | `.next/` |
| webui | test | `src/**/*`, `vitest.config.ts` | — |
| webui | typecheck | `src/**/*`, `tsconfig.json`, `next-env.d.ts` | — |

**原则**：inputs 越精确，缓存命中率越高。outputs 声明后 Nx 可恢复缓存产物。

### 3.4 Nx Cloud 远程缓存

已配置 `nxCloudId`，CI 和本地共享缓存。同一 commit 的构建结果只需执行一次。

## 四、Affected 机制

```bash
# 只对 git 变更影响的项目执行任务
pnpm nx affected -t test
pnpm nx affected -t build
pnpm nx affected -t check
```

Nx 通过项目依赖图计算哪些项目受变更影响。例如：
- 改 `apps/webui/src/` → 只影响 webui
- 改 `packages/core/` → 影响所有依赖 core 的项目
- 改根 `tsconfig.base.json` → 影响所有 TS 项目

**日常开发用 affected，CI 用 run-many 全量。**

## 五、项目配置模式

### 5.1 Maven 项目桥接（service）

Java 项目没有原生 Nx 插件支持，通过 `project.json` 的 `command` 字段桥接：

```json
{
  "targets": {
    "build": {
      "command": "mvnw.cmd package -DskipTests",
      "options": { "cwd": "apps/service" },
      "cache": true,
      "inputs": ["{projectRoot}/**/src/**/*", "{projectRoot}/**/pom.xml"],
      "outputs": ["{projectRoot}/**/target"]
    }
  }
}
```

关键点：
- `cwd` 指向 Maven 根目录
- `inputs` 包含所有子模块的 `src/` 和 `pom.xml`
- `outputs` 包含所有子模块的 `target/`
- Windows 用 `mvnw.cmd`，跨平台需条件处理

### 5.2 Next.js 项目（webui）

使用 `@nx/next/plugin` 自动推断 build/dev/start target，同时在 `project.json` 中自定义 typecheck/test/check 等额外 target。

### 5.3 共享包（v0.2+ 模式）

```json
{
  "name": "@aaf/core",
  "projectType": "library",
  "targets": {
    "build": {
      "dependsOn": ["^build"],
      "command": "tsdown",
      "inputs": ["production"],
      "outputs": ["{projectRoot}/dist/**"]
    },
    "test": {
      "dependsOn": ["^build"],
      "inputs": ["test"],
      "outputs": ["{projectRoot}/coverage/**"]
    }
  }
}
```

`^build` 表示先构建所有上游依赖包，保证 workspace 引用已构建。

## 六、依赖版本统一

### 6.1 当前状态

根 `package.json` 的 `dependencies` 声明了 react/next 等核心依赖版本，各 app 的 `package.json` 也各自声明版本。

### 6.2 推荐优化：pnpm.overrides

在根 `package.json` 添加全局版本锁定：

```json
{
  "pnpm": {
    "overrides": {
      "react": "^19.0.0",
      "react-dom": "^19.0.0",
      "@types/react": "^19.0.0",
      "typescript": "~5.9.0"
    }
  }
}
```

**效果**：无论哪个包声明了这些依赖，最终安装的版本都由 overrides 统一控制，避免版本漂移。

## 七、测试分层与 Nx Target 映射

| 层 | 命名 | 执行器 | Nx Target | 负责人 |
|----|------|--------|-----------|--------|
| Java 单测 | `*Test.java` | Surefire | `test` | developer |
| Java 集成/验收 | `*IT.java` / `*AcceptanceTest.java` | Failsafe | `acceptance` | tester |
| TS 单测 | `*.test.ts(x)` / `*.spec.ts(x)` | Vitest | `test` | developer |
| TS 验收 | `*.accept.test.ts(x)` | Vitest (acceptance config) | `acceptance` | tester |

**规则**：`pnpm nx test` = developer 自验证；`pnpm nx acceptance` = tester 验收。两者配置独立，互不干扰。

## 八、CI 集成模式

```yaml
# GitHub Actions 示例
- run: pnpm nx affected -t check --base=origin/main
- run: pnpm nx affected -t acceptance --base=origin/main
```

- PR 用 `affected`（只跑变更影响的项目）
- Release 用 `run-many`（全量验证）
- Nx Cloud 提供分布式任务执行（DTE），大型 CI 可拆分到多台机器

## 九、后续优化路径

### 9.1 v0.1.0 可立即执行

| 优化项 | 预期收益 | 复杂度 |
|--------|---------|--------|
| namedInputs 精细化 | 改文档不触发重建，缓存命中率提升 | 低 |
| pnpm.overrides 统一版本 | 消除版本漂移风险 | 低 |
| targetDefaults 统一 | 减少各 project.json 重复配置 | 低 |

### 9.2 v0.2+ packages/ 落地时

| 优化项 | 预期收益 | 复杂度 |
|--------|---------|--------|
| 共享 tsconfig 包 (`packages/_config/tsconfig/`) | 统一 TS 配置，新包零配置 | 中 |
| `^build` 依赖编排 | 包间构建顺序自动化 | 低 |
| publint + attw 发布检查 | 包质量门控 | 低 |
| Changeset 版本管理 | 多包版本自动化 | 中 |
| enforce-module-boundaries | 依赖方向 lint 强制 | 中 |

### 9.3 长期演进

| 优化项 | 预期收益 | 触发条件 |
|--------|---------|---------|
| lefthook 替代 husky | 并行 hook 执行更快 | 团队规模扩大 / hook 执行时间成为瓶颈 |
| Nx DTE（分布式任务执行） | CI 时间线性缩短 | CI 时间 > 10 分钟 |
| 自定义 Nx 生成器 | 新模块脚手架标准化 | 模块数量 > 10 |

## 十、常见问题

### Q: 为什么不直接用 `mvn test` 而要 `pnpm nx test service`？

统一入口带来三个好处：
1. **缓存**：相同 inputs 不重复执行
2. **affected**：只跑受变更影响的项目
3. **编排**：自动处理项目间依赖顺序

### Q: 缓存没命中怎么排查？

```bash
# 查看某个 target 的 inputs hash
pnpm nx test service --verbose

# 清除本地缓存重跑
pnpm nx reset
```

常见原因：inputs 配置过宽（如包含了 `.next/` 等生成目录）、环境变量变化、依赖版本变化。

### Q: 新增一个 packages/ 共享包需要做什么？

1. 在 `packages/{name}/` 创建 `package.json` + `project.json` + `tsconfig.json`
2. `pnpm-workspace.yaml` 已配置 `packages/*`，自动识别
3. 在 `project.json` 中定义 build/test/typecheck target
4. 消费方 `package.json` 添加 `"@aaf/{name}": "workspace:*"` 依赖
5. 运行 `pnpm install` 链接

### Q: Windows 和 Linux/Mac 命令不一致怎么办？

当前 service 的 `project.json` 用 `mvnw.cmd`。跨平台方案：
- 使用 `npx nx-maven` 插件（`@nx/maven` 已安装）
- 或在 `command` 中用条件脚本：`node -e "require('child_process').execSync(process.platform==='win32'?'mvnw.cmd':'./mvnw')+' test'"`

当前阶段 Windows 开发为主，暂不处理。CI 环境如需 Linux 再适配。

---

> 参考来源：[CopilotKit Nx monorepo 分析](../../design/apps/webui/copilotkit-nx-monorepo-analysis.md) | [Nx 官方文档](https://nx.dev/concepts)
