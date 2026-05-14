---
title: uni-ku/root
iframe: true
iframeFormatter: subPages/uni-ku-root/index
---
# @uni-ku/root

在传统的 UniApp 开发中，由于框架限制，无法像标准 Vue 应用那样使用全局根组件来管理公共状态和组件，这给开发带来了诸多不便。

为了解决这个问题，我们引入了 [@uni-ku/root](https://github.com/uni-ku/root) 插件，它通过 Vite 模拟出虚拟根组件，让 UniApp 项目也能享受到类似 Vue 标准应用的开发体验，也可以解决 `@uni-helper/vite-plugin-uni-layouts` 插件无法使用微信小程序 `page-meta` 的问题。

## 🎯 @uni-ku/root 是什么？

@uni-ku/root 借助 Vite 模拟出虚拟根组件(支持SFC的App.vue)，解决 uniapp 无法使用公共组件问题。

### 🎏 支持

- 自定义虚拟根组件文件命名(App.ku.vue文件命名支持更换)
- 更高灵活度的获取虚拟根组件实例(获取KuRootView的Ref)
- 自动提取PageMeta到页面顶层(自动提升小程序PageMeta[用于阻止滚动穿透]组件)

## 🚀 接入步骤和配置

### 📦 安装

```bash
pnpm add -D @uni-ku/root

yarn add -D @uni-ku/root

npm install -D @uni-ku/root
```

### 🚀 Vite 配置

在 `vite.config.ts` 中引入并配置 UniKuRoot 插件：

```typescript
import { defineConfig } from 'vite'
import Uni from '@dcloudio/vite-plugin-uni'
import UniHelperManifest from '@uni-helper/vite-plugin-uni-manifest'
import UniHelperPages from '@uni-helper/vite-plugin-uni-pages'
import UniHelperLayouts from '@uni-helper/vite-plugin-uni-layouts'
import UniHelperComponents from '@uni-helper/vite-plugin-uni-components'
import AutoImport from 'unplugin-auto-import/vite'
import { WotResolver } from '@uni-helper/vite-plugin-uni-components/resolvers'
import UniKuRoot from '@uni-ku/root'
// https://vitejs.dev/config/
export default async () => {
  const UnoCSS = (await import('unocss/vite')).default
  return defineConfig({
    plugins: [
      // https://github.com/uni-helper/vite-plugin-uni-manifest
      UniHelperManifest(),
      // https://github.com/uni-helper/vite-plugin-uni-pages
      UniHelperPages({
        dts: 'src/uni-pages.d.ts',
        subPackages: [
          'src/subPages',
        ],
        /**
         * 排除的页面，相对于 dir 和 subPackages
         * @default []
         */
        exclude: ['**/components/**/*.*'],
      }),
      // https://github.com/uni-helper/vite-plugin-uni-layouts
      UniHelperLayouts(),
      // https://github.com/uni-helper/vite-plugin-uni-components
      UniHelperComponents({
        resolvers: [WotResolver()],
        dts: 'src/components.d.ts',
        dirs: ['src/components', 'src/business'],
        directoryAsNamespace: true,
      }),
      // https://github.com/uni-ku/root
      UniKuRoot(),
      Uni(),
      // https://github.com/antfu/unocss
      // see unocss.config.ts for config
      UnoCSS(),
    ],
  })
}

```

**重要提示**：UniKuRoot 插件必须放在 `Uni()` 插件之前，如果存在修改 pages.json 的插件和 `Layout` 插件，需要将 UniKuRoot 放在它们之后。

### 🎉 创建虚拟根组件

在 `src/App.ku.vue` 中创建虚拟根组件：
> 注意 `App.ku.vue` 中暂时无法编写样式全局生效，所以我们可以将样式写到 `App.vue` 中

```html
<script setup lang="ts">
const { themeVars, theme } = useManualTheme()
</script>

<template>
  <wd-config-provider :theme-vars="themeVars" :theme="theme" :custom-class="`page-wraper ${theme}`">
    <ku-root-view />
    <wd-notify />
    <wd-message-box />
    <wd-toast />
    <global-loading />
    <global-toast />
    <global-message />
    <!-- #ifdef MP-WEIXIN -->
    <privacy-popup />
    <!-- #endif -->
  </wd-config-provider>
</template>
```

## 💡 代码示例和使用方法

### 1. 在页面中使用全局 Toast

```html
<!-- src/pages/index/index.vue -->
<script setup lang="ts">
const globalToast = useGlobalToast()

function showSuccess() {
  globalToast.success('操作成功！')
}

function showError() {
  globalToast.error('操作失败！')
}
</script>

<template>
  <view>
    <button @click="showSuccess">显示成功提示</button>
    <button @click="showError">显示错误提示</button>
  </view>
</template>
```

### 2. PageMeta 自动提升示例

在页面中使用 PageMeta 组件，会自动提升到页面顶层：

```html
<!-- src/pages/uni-ku-root/index.vue -->
<script setup lang="ts">
definePage({
  name: 'root',
  style: {
    navigationBarTitleText: 'uni-ku/root',
  },
})

const show = ref<boolean>(false)
</script>

<template>
  <page-meta :page-style="`overflow:${show ? 'hidden' : 'visible'};`" />
  <view class="min-h-200vh">
    <demo-block title="锁定滚动" transparent>
      <wd-cell-group border>
        <wd-cell title="锁定滚动" is-link @click="show = !show" />
      </wd-cell-group>
    </demo-block>

    <wd-popup
      v-model="show"
      lock-scroll
      position="bottom"
      closable
      :safe-area-inset-bottom="true"
      custom-style="height: 200px;"
      @close="show = false"
    />
  </view>
</template>

```


## 🎉 总结

通过接入 `@uni-ku/root`，wot-starter 项目成功实现了：

- ✅ 全局组件的统一管理
- ✅ 主题配置的全局应用
- ✅ 更好的代码组织结构
- ✅ 接近标准 Vue 应用的开发体验
- ✅ 完美支持 Wot UI 组件库

这个方案不仅解决了 UniApp 开发中的痛点，还可以解决 `@uni-helper/vite-plugin-uni-layouts` 插件无法使用微信小程序 `page-meta` 的问题，一举多得，美哉！

## 🔗 相关链接

- [@uni-ku/root GitHub](https://github.com/uni-ku/root)
- [Wot UI](https://wot-ui.cn)
- [Wot Starter](https://starter.wot-ui.cn)
