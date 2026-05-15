---
level: Practice
layer: Product
purpose: AAF uniapp 分包优化指南（@uni-ku/bundle-optimizer）
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 理解为什么需要 bundle-optimizer
  - 掌握跨分包异步引用组件的正确姿势
---

# 分包优化

uni-app Vue3（Vite 构建）移除了 Vue2 时代的自动拆包逻辑，导致所有第三方库打进 `common/vendor.js`，主包轻易超过 2MB 限制。`@uni-ku/bundle-optimizer` 补回了这个能力。

> 项目已集成，`vite.config.ts` 和 `manifest.config.ts` 均已配置，无需额外安装。

## 分包配置

`vite.config.ts` 中定义了三个分包：

```typescript
UniHelperPages({
  subPackages: ['src/subPages', 'src/subEcharts', 'src/subAsyncEcharts'],
})
```

`manifest.config.ts` 中开启微信小程序分包优化：

```typescript
'mp-weixin': {
  optimization: { subPackages: true },
}
```

## 跨分包异步引用组件

分包之间不能直接同步引用，需用 `componentPlaceholder`：

```vue
<script setup lang="ts">
import BarChart from '@/subEcharts/echarts/components/BarChart.vue'

defineOptions({
  componentPlaceholder: { BarChart: 'view' },
})
</script>

<template>
  <BarChart />
</template>
```

> ⚠️ 不要用 `import('./Comp.vue').then(...)` 动态导入 Vue 文件，会导致页面空白。跨分包 JS/TS 模块可以用 `import()`，但 Vue 组件必须用 `componentPlaceholder`。

## 验证主包体积

```bash
pnpm nx run uniapp:build:mp-weixin
# 用微信开发者工具「构建分析」查看主包大小，目标 < 2MB
```
