---
level: Practice
layer: Product
purpose: CopilotKit Nx monorepo 工程化实践分析与 AAF 借鉴建议
status: draft
version: 1.0.0
date: 2026-05-10
author: AaronZZH
gains:
  - 了解 CopilotKit 如何用 Nx 管理大型 monorepo
  - 提取值得 AAF 直接借鉴的工程化实践
  - 明确 AAF 当前阶段应采纳哪些、暂缓哪些
---

# CopilotKit Nx Monorepo 工程化实践分析

> 源码位置：`tmp/nextjs/CopilotKit/`，分析时间：2026-05-10。
> 关注点：Nx 配置、构建流水线、质量门控、AI 协作规范，不分析业务逻辑。

## 一、整体架构

```text
CopilotKit/
├── packages/          → 19 个发布包（@copilotkit/* 作用域）
├── examples/          → 示例项目（v1/v2/integrations/showcases）
├── showcase/          → 多 shell 展示站（shell/shell-dojo/shell-docs/shell-dashboard）
├── docs/              → 文档站（独立 Next.js，独立 pnpm workspace）
├── scripts/           → 工程脚本（release/qa/hooks/parity）
├── codemods/          → 代码迁移工具
├── sdk-python/        → Python SDK（独立 poetry 项目）
├── nx.json            → Nx 配置
├── package.json       → 根 package.json（devDependencies + scripts）
└── pnpm-workspace.yaml → workspace 范围声明
```

**技术栈**：Nx 22 + pnpm 10 + TypeScript 5 + tsdown（构建）+ oxlint + oxfmt（lint/format）+ lefthook（Git hooks）+ Vitest（测试）+ Changeset（版本管理）

---

## 二、Nx 配置详解（nx.json）

### 2.1 namedInputs — 精细化缓存输入

```json
{
  "namedInputs": {
    "default": [
      "{projectRoot}/**/*",
      "!{projectRoot}/node_modules/**/*",
      "!{projectRoot}/**/*.md",    // 排除文档变更
      "!{projectRoot}/**/*.yml",   // 排除配置变更
      "!{projectRoot}/**/*.yaml"
    ],
    "production": [
      "default",
      "!{projectRoot}/**/*.test.*",  // 排除测试文件
      "!{projectRoot}/**/*.spec.*",
      "!{projectRoot}/**/__tests__/**"
    ],
    "test": [
      "{projectRoot}/src/**/*.ts",
      "{projectRoot}/src/**/*.tsx",
      "{projectRoot}/**/__tests__/**",
      "{projectRoot}/**/*.test.*",
      "{projectRoot}/**/*.spec.*"
    ]
  }
}
```

**关键设计**：
- `default` 排除 `.md`/`.yml` 文件 → 改文档不触发重新构建
- `production` 在 `default` 基础上排除测试文件 → 测试文件变更不触发生产构建
- `test` 只包含源码和测试文件 → 最小化测试缓存失效范围

### 2.2 targetDefaults — 统一 target 行为

```json
{
  "targetDefaults": {
    "build": {
      "dependsOn": ["^build"],        // 先构建所有依赖包
      "inputs": ["production", "{projectRoot}/.env*"],
      "outputs": ["{projectRoot}/dist/**"],
      "cache": true
    },
    "dev": {
      "dependsOn": ["^build"],        // dev 模式也先构建依赖
      "cache": false                  // dev 不缓存
    },
    "test": {
      "dependsOn": ["^build"],        // 测试前先构建依赖
      "inputs": ["test"],
      "outputs": ["{projectRoot}/coverage/**"],
      "cache": true
    },
    "check-types": {
      "dependsOn": ["^build", "^check-types"],  // 类型检查依赖上游类型
      "cache": true
    },
    "publint": {
      "dependsOn": ["build"],         // 发布检查依赖构建产物
      "inputs": ["{projectRoot}/package.json", "{projectRoot}/dist/**"],
      "cache": true
    },
    "attw": {
      "dependsOn": ["build"],         // 类型导出检查依赖构建产物
      "cache": true
    }
  },
  "parallel": 14                      // 最大并行任务数
}
```

**关键设计**：
- `^build` 表示先构建所有上游依赖包，保证 workspace 引用的包已构建
- `publint` + `attw` 是发布前的包质量检查（见下文）
- `parallel: 14` 充分利用多核

---

## 三、包构建方案：tsdown

CopilotKit 用 **tsdown**（基于 Rolldown/Rust）替代了 tsc + rollup 的传统方案。

### 3.1 多格式输出

每个包同时输出 ESM、CJS、UMD 三种格式：

```typescript
// packages/react-core/tsdown.config.ts
export default defineConfig([
  {
    entry: ["src/index.tsx", "src/v2/index.ts"],
    format: ["esm", "cjs"],
    dts: true,          // 生成 .d.ts 类型声明
    sourcemap: true,
    target: "es2022",
    outDir: "dist",
    external: ["react", "react-dom", ...],  // peer deps 不打包
  },
  {
    entry: ["src/index.tsx"],
    format: ["umd"],
    globalName: "CopilotKitReactCore",
    codeSplitting: false,
    // UMD 需要声明全局变量映射
    outputOptions(options) {
      options.globals = { react: "React", ... };
    },
  },
]);
```

### 3.2 子路径导出（Package Exports）

```json
{
  "exports": {
    ".": {
      "import": "./dist/index.mjs",
      "require": "./dist/index.cjs"
    },
    "./v2": {
      "import": "./dist/v2/index.mjs",
      "require": "./dist/v2/index.cjs"
    },
    "./v2/context": { ... },
    "./v2/headless": { ... },
    "./v2/styles.css": "./dist/v2/index.css",
    "./package.json": "./package.json"
  }
}
```

子路径导出允许按需引入，避免全量导入。`./v2/headless` 是无 UI 的纯逻辑包，供 React Native 使用。

### 3.3 发布质量检查

```bash
pnpm publint    # 检查 package.json exports 字段是否正确
pnpm attw       # 检查 TypeScript 类型导出是否符合 node16 模块解析
```

这两个工具在 CI 和 pre-commit 中都会运行，确保发布的包可被正确消费。

---

## 四、共享配置包

### 4.1 typescript-config 包

```
packages/typescript-config/
├── base.json          → 基础 tsconfig（strict + NodeNext + ES2022）
├── nextjs.json        → Next.js 应用扩展
└── react-library.json → React 库扩展（添加 jsx: react-jsx）
```

各包的 `tsconfig.json` 继承共享配置：
```json
{
  "extends": "@copilotkit/typescript-config/react-library.json",
  "include": ["src"],
  "exclude": ["node_modules", "dist"]
}
```

### 4.2 tailwind-config 包

```
packages/tailwind-config/
└── index.js    → 共享 Tailwind 配置（颜色/字体/插件）
```

各包的 `tailwind.config.js` 扩展共享配置，保证设计系统一致性。

---

## 五、Git Hooks：lefthook

CopilotKit 用 **lefthook** 替代 husky，配置更简洁，性能更好。

```yaml
# lefthook.yml
pre-commit:
  parallel: true          # 并行执行所有 pre-commit 命令
  commands:
    check-binaries:
      run: bash scripts/hooks/check-binaries.sh

    sync-lockfile:
      glob: "{packages,examples}/**/package.json"
      run: pnpm i --lockfile-only   # package.json 变更时自动同步 lockfile
      stage_fixed: true             # 自动 stage 修改的文件

    lint-fix:
      glob: "*.{js,jsx,ts,tsx,mjs,cjs}"
      exclude: ["docs/**"]
      run: |
        if [ -n "{staged_files}" ]; then
          pnpm exec oxlint --fix {staged_files} && pnpm exec oxfmt --write {staged_files}
        fi
      stage_fixed: true             # 自动 stage lint 修复的文件

    test-and-check-packages:
      run: pnpm run test && pnpm run check:packages

commit-msg:
  commands:
    commitlint:
      run: pnpm commitlint --edit {1}
```

**关键设计**：
- `parallel: true` 并行执行，不串行等待
- `stage_fixed: true` 自动 stage 被 hook 修改的文件，无需手动 `git add`
- `glob` 过滤只对相关文件执行，避免全量扫描
- `{staged_files}` 只处理暂存文件，不处理整个仓库

---

## 六、Lint/Format：oxlint + oxfmt

CopilotKit 用 **oxlint**（Rust）+ **oxfmt**（Rust）替代 ESLint + Prettier。

```json
// .oxlintrc.json
{
  "plugins": ["typescript", "unicorn", "oxc", "react", "nextjs", "import"],
  "jsPlugins": ["./packages/react-ui/oxlint-rules/copilotkit-plugin.mjs"],
  "categories": {
    "correctness": "warn",
    "suspicious": "warn"
  },
  "rules": {
    "typescript/consistent-type-imports": ["warn", { "prefer": "type-imports" }],
    "react/self-closing-comp": "warn"
  },
  "overrides": [
    {
      "files": ["packages/**/*.{ts,tsx}"],
      "rules": {
        "no-restricted-imports": ["error", { ... }]  // 禁止特定导入
      }
    },
    {
      "files": ["packages/react-ui/src/**/*.{ts,tsx}"],
      "rules": {
        "copilotkit/require-cpk-prefix": "warn"  // 自定义规则：组件名必须有前缀
      }
    }
  ]
}
```

**亮点**：自定义 oxlint 插件（`copilotkit-plugin.mjs`）强制组件命名规范，这是 ESLint 插件的 oxlint 等价物。

---

## 七、版本管理：Changeset

```
.changeset/
├── config.json
├── little-pears-tell.md    → 每个 PR 对应一个 changeset 文件
├── five-avocados-visit.md
└── ...
```

Changeset 工作流：
1. PR 合并时附带 `.changeset/*.md` 文件（描述变更类型和影响包）
2. `release:prepare` 脚本聚合所有 changeset → 计算版本号 → 生成 CHANGELOG
3. CI 自动发布到 npm

---

## 八、发布脚本

```typescript
// scripts/release/prepare-release.ts
// 自动化发布准备：
// 1. 读取当前版本
// 2. 根据 --bump patch|minor|major 计算下一版本
// 3. 批量更新所有包的 package.json 版本号
// 4. 从 git log 生成 release notes（按 feat/fix/other 分类）
// 5. --dry-run 模式预览不实际写入
```

```typescript
// scripts/validate-integration-pins.ts
// 验证示例项目中的 @copilotkit/* 依赖版本是否与当前发布版本一致
// 防止示例项目使用过时的 SDK 版本
```

---

## 九、Parity 验证系统

CopilotKit 有一套独特的**集成示例一致性验证**机制：

```
examples/integrations/
├── _parity/
│   ├── manifest.yaml      → 定义哪些文件应该在所有集成中保持一致
│   ├── north-star/        → 标准参考实现（"北极星"）
│   ├── sync.ts            → 将北极星文件同步到所有集成
│   └── verify.ts          → 验证所有集成与北极星的一致性
├── langgraph-js/          → LangGraph JS 集成示例
├── langgraph-python/      → LangGraph Python 集成示例
├── adk/                   → Google ADK 集成示例
└── ...
```

**工作原理**：
- `north-star/` 是标准实现，其他集成通过 `sync.ts` 同步公共文件
- `verify.ts` 检查文件字节一致性、package.json 关键字段、Agent 工具名称
- CI 运行 `parity:check` 防止集成示例与标准实现产生漂移

---

## 十、AI 协作规范（.claude/ 目录）

```
.claude/
├── docs/
│   ├── architecture.md    → 架构说明（三层架构、包职责、请求生命周期）
│   ├── hooks.md           → Hook 开发检查清单
│   ├── workflow.md        → AI 工作流程（何时规划、何时自主修复）
│   └── git.md             → Git worktree 工作流
├── skills/                → 可复用技能
└── specs/                 → 功能规格
```

**workflow.md 核心规则**：
```
- Bug 修复（< 5 文件）：自主修复，不需要规划
- 大型 Bug（5+ 文件或架构影响）：先进入规划模式
- 新功能和重构：始终先规划，获得用户确认后再实现
- 任务完成前必须证明它有效（运行测试）
- 每次被用户纠正后：更新 tasks/lessons.md 记录模式和规则
```

**AGENTS.md 核心规则**：
```
- 始终通过 nx 运行任务（nx run, nx run-many, nx affected）
- 扁平包结构：所有包在 packages/ 下，@copilotkit/ 作用域
- 优先简单正确的方案
- 始终在 git worktree 中工作（隔离）
- private-agents.md：个人 AI 指令文件（gitignore，不共享）
```

---

## 十一、pnpm workspace 的特殊处理

```yaml
# pnpm-workspace.yaml
packages:
  - "packages/*"
  - "examples/v1/*"
  - "examples/v2/*"
  - "examples/v2/*/apps/*"
  # showcase/shell-dashboard 故意不在 workspace 中
  # 它是独立的 Next.js 应用，用 npm ci 构建，有自己的 package-lock.json
  # 加入 workspace 会破坏其独立部署路径
  - "showcase/scripts"
  - "showcase/harness"
```

**关键设计**：`showcase/shell-dashboard` 故意排除在 workspace 之外，保持其独立部署能力。这说明 monorepo 不是"所有东西都必须在 workspace 里"，独立部署的应用可以保持独立。

```json
// package.json pnpm.overrides
{
  "react": "19.2.3",
  "react-dom": "19.2.3",
  "next": "^16.0.10",
  "@types/react": "19.1.8"
}
```

全局 overrides 统一所有包的 React/Next.js 版本，避免版本冲突。

---

## 十二、对 AAF 的借鉴建议

### 12.1 立即可借鉴（v0.1.0）

| 实践 | CopilotKit 做法 | AAF 当前状态 | 建议 |
|------|----------------|-------------|------|
| **namedInputs 精细化** | 排除 `.md`/`.yml`/测试文件 | 未配置 | 在 `nx.json` 中添加 `namedInputs`，改文档不触发重建 |
| **共享 tsconfig 包** | `packages/typescript-config/` | 无 | 创建 `packages/tsconfig/`，统一 base/nextjs/library 三种配置 |
| **pnpm.overrides 统一版本** | 全局锁定 React/Next.js 版本 | 未配置 | 在根 `package.json` 添加 `pnpm.overrides` 统一关键依赖版本 |
| **lefthook 替代 husky** | parallel + stage_fixed + glob 过滤 | 使用 husky | 评估迁移，lefthook 并行执行更快，`stage_fixed` 更方便 |
| **oxlint + oxfmt** | 替代 ESLint + Prettier | 使用 Biome | AAF 已选 Biome，与 oxlint 同类，无需迁移 |
| **AI 协作规范文档** | `.claude/docs/` 分类文档 | `.kiro/` 已有 | 参考 workflow.md 的"何时规划/何时自主"规则，补充到 AGENTS.md |
| **private-agents.md 机制** | 个人 AI 指令文件（gitignore） | 无 | 在 `.gitignore` 中添加 `private-agents.md`，允许个人定制 |

### 12.2 中期借鉴（v0.2+ packages/ 落地时）

| 实践 | CopilotKit 做法 | AAF 建议 |
|------|----------------|---------|
| **tsdown 多格式构建** | ESM + CJS + UMD + .d.ts | packages/ 包发布时采用，当前 apps/ 不需要 |
| **子路径导出** | `./v2`, `./v2/headless`, `./v2/styles.css` | packages/chat 等包设计时参考 |
| **publint + attw** | 发布前检查包质量 | packages/ 包发布前必跑 |
| **Changeset 版本管理** | PR 附带 changeset 文件 | packages/ 开始发布时引入 |
| **共享 tailwind-config 包** | 统一设计 token | packages/ui 落地时创建 |

### 12.3 暂缓或不适用

| 实践 | 原因 |
|------|------|
| **Parity 验证系统** | AAF 当前无多集成示例场景，过度工程化 |
| **多 showcase shell** | AAF 当前单一前端，不需要多 shell 策略 |
| **UMD 格式输出** | AAF 不发布 CDN 包，不需要 UMD |
| **validate-integration-pins** | AAF 无外部集成示例需要版本锁定验证 |
| **git worktree 工作流** | 团队规模小，PR 分支工作流已足够 |

---

## 十三、立即可执行的改进项

### 改进 1：优化 nx.json namedInputs

```json
// nx.json
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
      "{projectRoot}/**/__tests__/**",
      "{projectRoot}/**/*.test.*",
      "{projectRoot}/**/*.spec.*"
    ]
  }
}
```

### 改进 2：添加 pnpm.overrides 统一版本

```json
// package.json
{
  "pnpm": {
    "overrides": {
      "react": "^19.0.0",
      "react-dom": "^19.0.0",
      "@types/react": "^19.0.0",
      "typescript": "~5.8.0"
    }
  }
}
```

### 改进 3：创建共享 tsconfig 包

```
packages/tsconfig/
├── package.json
├── base.json          → 严格模式基础配置
├── nextjs.json        → Next.js 应用配置
└── library.json       → 共享库配置（含 jsx: react-jsx）
```

### 改进 4：补充 private-agents.md 到 .gitignore

```
# .gitignore
private-agents.md
tasks/lessons.md
```

允许每个开发者维护自己的 AI 指令文件，不影响团队共享规范。

---

## 附：CopilotKit vs AAF 工程化对比

| 维度 | CopilotKit | AAF |
|------|-----------|-----|
| 包管理器 | pnpm 10 | pnpm（同） |
| 构建工具（包） | tsdown（Rust） | —（待定） |
| Lint | oxlint（Rust） | Biome（同类） |
| Format | oxfmt（Rust） | Biome（同类） |
| Git Hooks | lefthook | husky + lint-staged |
| 版本管理 | Changeset | — |
| 类型检查 | tsc --noEmit | tsc --noEmit（同） |
| 测试 | Vitest | Vitest（同） |
| AI 协作规范 | `.claude/docs/` | `.kiro/steering/` |
| 提交规范 | commitlint（conventional） | commitlint（同） |
| 包质量检查 | publint + attw | — |
