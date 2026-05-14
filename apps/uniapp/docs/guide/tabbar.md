---
title: 自定义 Tabbar
iframe: true
iframeFormatter: ''
---

# 自定义 Tabbar

本项目基于 [Wot UI](https://wot-ui.cn/) 的 `wd-tabbar` 组件，提供自定义 Tabbar 的实现。

## 实现原理

项目的自定义 Tabbar 主要由以下三个部分组成：

1. **配置文件** (`pages.config.ts`) - 启用自定义 Tabbar 并配置基础信息
2. **组件实现** (`src/layouts/tabbar.vue`) - 自定义 Tabbar 的视图层
3. **状态管理** (`src/composables/useTabbar.ts`) - Tabbar 的逻辑和状态管理

当前实现中，`tabbar.vue` 会在 APP 端调用 `uni.hideTabBar()` 隐藏原生 Tabbar，并通过 `router.pushTab` 切换页面。

## 添加 Tabbar 项

### 1. 修改配置文件

在 `pages.config.ts` 中的 `tabBar.list` 数组中添加新的页面路径：

```typescript
tabBar: {
  custom: true,
  // ... 其他配置
  list: [{
    pagePath: 'pages/index/index',
  }, {
    pagePath: 'pages/about/index',
  }, {
    // 添加新的tabbar项
    pagePath: 'pages/new-page/index',
  }],
}
```

### 2. 更新状态管理

在 `src/composables/useTabbar.ts` 中的 `tabbarItems` 数组中添加对应的配置：

```typescript
const tabbarItems = ref<TabbarItem[]>([
  { name: 'home', active: true, title: '首页', icon: 'home' },
  { name: 'about', active: false, title: '关于', icon: 'user' },
  // 添加新的tabbar项
  { name: 'new-page', active: false, title: '新页面', icon: 'star' },
])
```

### 3. TabbarItem 接口说明

```typescript
interface TabbarItem {
  name: string // 页面名称，对应路由name
  value?: number // 徽标数值，不传表示不显示徽标
  active: boolean // 是否为当前激活项
  title: string // 显示标题
  icon: string // 图标名称
}
```

## 配置图标

### 图标来源

项目使用 [Wot UI](https://wot-ui.cn/) 的内置图标库。你可以通过以下方式查看可用图标：

1. 访问 [Wot UI 图标文档](https://wot-ui.cn/component/icon.html)
2. 查看所有可用的图标名称

### 修改图标

在 `src/composables/useTabbar.ts` 中修改对应项的 `icon` 字段：

```typescript
const tabbarItems = ref<TabbarItem[]>([
  { name: 'home', active: true, title: '首页', icon: 'home' },
  { name: 'about', active: false, title: '关于', icon: 'user' },
])
```

### 常用图标示例

```typescript
// 常用的tabbar图标
'home' // 首页
'user' // 用户/关于
'shopping-bag' // 购物
'star' // 收藏
'setting' // 设置
'message' // 消息
'search' // 搜索
'calendar' // 日历
```

### 使用自定义图标

如果内置图标不能满足需求，你可以使用 `wd-tabbar-item` 的 `icon` 插槽来自定义图标。

#### 修改 tabbar.vue 组件

在 `src/layouts/tabbar.vue` 中使用插槽自定义图标：

```vue
<template>
  <wd-tabbar
    :model-value="activeTabbar.name"
    placeholder
    bordered
    safe-area-inset-bottom
    fixed
    @change="handleTabbarChange"
  >
    <wd-tabbar-item
      v-for="(item, index) in tabbarList"
      :key="index"
      :name="item.name"
      :value="getTabbarItemValue(item.name)"
      :title="item.title"
    >
      <!-- 使用 icon 插槽自定义图标 -->
      <template #icon="{ active }">
        <image
          :src="active ? item.activeIcon : item.inactiveIcon"
          class="custom-icon"
        />
      </template>
    </wd-tabbar-item>
  </wd-tabbar>
</template>

<style>
.custom-icon {
  width: 22px;
  height: 22px;
}
</style>
```

#### 更新 TabbarItem 接口

```typescript
interface TabbarItem {
  name: string // 页面名称，对应路由name
  value?: number // 徽标数值，不传表示不显示徽标
  active: boolean // 是否为当前激活项
  title: string // 显示标题
  icon: string // 图标名称（使用内置图标时）
  activeIcon?: string // 激活状态自定义图标路径
  inactiveIcon?: string // 未激活状态自定义图标路径
}
```

#### 配置自定义图标

```typescript
const tabbarItems = ref<TabbarItem[]>([
  {
    name: 'home',
    active: true,
    title: '首页',
    icon: 'home',
    activeIcon: '/static/icons/home-active.png',
    inactiveIcon: '/static/icons/home.png'
  },
  {
    name: 'about',
    active: false,
    title: '关于',
    icon: 'user',
    activeIcon: '/static/icons/about-active.png',
    inactiveIcon: '/static/icons/about.png'
  },
])
```

> 📖 **了解更多**: 查看 [wd-tabbar 组件文档](https://wot-ui.cn/component/tabbar.html) 了解更多自定义选项和插槽用法。

## 徽标配置

自定义 Tabbar 支持显示徽标，通过 `setTabbarItem` 方法为 Tabbar 项设置徽标数值。

### 显示徽标

使用 `setTabbarItem` 方法为 Tabbar 项设置徽标数值：

```typescript
const { setTabbarItem } = useTabbar()

// 为 'about' 页面设置徽标数值为 5
setTabbarItem('about', 5)
```

### 清除徽标

当前 `setTabbarItem` 的类型签名是 `setTabbarItem(name: string, value: number)`，文档示例只演示数字徽标。

如果你希望支持显式清除（例如传 `undefined` 或 `null`），建议先将 `useTabbar.ts` 中的签名改为 `value?: number` 或 `value: number | null`，再在业务中调用。

### 在组件中使用

```vue
<script setup>
const { setTabbarItem } = useTabbar()

// 模拟收到新消息
function onNewMessage() {
  setTabbarItem('about', 3) // 显示3条未读消息
}

// 消息已读
function onMessageRead() {
  // 当前实现没有“清除徽标”的独立方法，可按业务约定设置数字值
  setTabbarItem('about', 1)
}
</script>
```

## 取消自定义 Tabbar

如果你想恢复使用 uni-app 原生 Tabbar，按以下步骤操作：

### 1. 修改配置文件

在 `pages.config.ts` 中修改 `tabBar` 配置：

```typescript
tabBar: {
  custom: false,  // 改为 false 或直接删除此行
  color: '#7A7E83',
  selectedColor: '#3cc51f',
  backgroundColor: '#ffffff',
  borderStyle: 'black',
  list: [{
    pagePath: 'pages/index/index',
    text: '首页',
    iconPath: '/static/icon/home.png',           // 需要提供图标文件
    selectedIconPath: '/static/icon/home-active.png'
  }, {
    pagePath: 'pages/about/index',
    text: '关于',
    iconPath: '/static/icon/about.png',
    selectedIconPath: '/static/icon/about-active.png'
  }],
}
```

### 2. 准备图标资源

原生 Tabbar 需要提供图标文件，在 `static/icon/` 目录下放置相应的图标：

- 未选中状态图标：`home.png`、`about.png`
- 选中状态图标：`home-active.png`、`about-active.png`

### 3. 移除自定义组件

可以选择删除或注释以下文件：
- `src/layouts/tabbar.vue`
- `src/composables/useTabbar.ts`

### 4. 更新布局

如果使用了自定义 Tabbar 布局，需要相应调整页面布局文件。

## 注意事项

1. **平台兼容性**: 自定义 Tabbar 在所有平台都能正常工作，但在 APP 端会自动隐藏原生 Tabbar
2. **页面路由**: 确保 `pages.config.ts` 中的 `pagePath` 与实际页面文件路径一致
3. **路由名称**: `tabbarItems` 中的 `name` 需要与目标页面路由名一致，否则 `router.pushTab({ name })` 无法正确跳转
4. **徽标更新**: 徽标状态是响应式的，可以实时更新
5. **主题支持**: 自定义 Tabbar 完全支持明暗主题切换

## API 参考

### useTabbar()

返回的方法和属性：

```typescript
const {
  tabbarList, // 计算属性：Tabbar项列表
  activeTabbar, // 计算属性：当前激活的Tabbar项
  getTabbarItemValue, // 方法：获取指定项的徽标值
  setTabbarItem, // 方法：设置指定项的徽标值
  setTabbarItemActive, // 方法：设置指定项为激活状态
} = useTabbar()
```

### 方法详解

```typescript
// 获取徽标值
getTabbarItemValue(name: string): number | undefined

// 设置徽标值
setTabbarItem(name: string, value: number): void

// 设置激活状态
setTabbarItemActive(name: string): void
```

通过以上配置，你可以灵活地管理项目中的自定义 Tabbar，满足各种业务需求。
