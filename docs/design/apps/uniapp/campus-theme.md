---
level: Practice
layer: Product
purpose: AAF uniapp campus 主题设计规范，智能体开发时参考
status: published
version: 1.0.0
date: 2026-05-15
author: AaronZZH
gains:
  - 开发时直接使用正确的颜色、圆角、间距变量
  - 保持 UI 风格一致性
---

# Campus 主题设计规范

> 参考来源：`tmp/uniapp/school`（校园服务类应用 UI 设计稿）
> 实现文件：`src/themes/styles/campus.scss`、`src/uni.scss`

## 一、色彩

| 用途 | 色值 | SCSS 变量 | CSS 变量 |
|------|------|-----------|---------|
| 主色 | `#8e44ad` | `$campus-primary` | `var(--wot-primary-6)` |
| 主色浅背景 | `#f0e6ff` | `$campus-primary-light` | `var(--wot-filled-content)` |
| 渐变（紫→蓝） | `#8e44ad → #3498db` | `$campus-gradient` | — |
| 按钮文字 | `#ffffff` | — | `var(--wot-text-white)` |
| 正文 | `#1D1F29` | — | `var(--wot-text-main)` |
| 辅助文字 | `#868A9C` | — | `var(--wot-text-auxiliary)` |
| 分割线 | `#e0e0e0` | — | `var(--wot-divider-main)` |

### 渐变背景用法

```scss
background: $campus-gradient;
// 或
background: linear-gradient(180deg, #8e44ad, #3498db);
```

## 二、圆角

| 场景 | 值 | SCSS 变量 |
|------|-----|-----------|
| 卡片 | `24rpx`（12px） | `$campus-radius-card` |
| 按钮 | `16rpx`（8px） | `$campus-radius-btn` |
| 输入框 | `16rpx`（8px） | `$campus-radius-input` |
| 小标签 | `8rpx` | `$campus-radius-sm` |

## 三、阴影

| 场景 | 值 | SCSS 变量 |
|------|-----|-----------|
| 卡片 | `0 4rpx 16rpx rgba(0,0,0,0.08)` | `$campus-shadow-card` |
| 浮层/FAB | `0 8rpx 24rpx rgba(142,68,173,0.15)` | `$campus-shadow-float` |

## 四、间距

| 名称 | 值 | SCSS 变量 |
|------|-----|-----------|
| 页面左右内边距 | `32rpx`（16px） | `$campus-page-padding` |
| XL | `48rpx` | `$campus-spacing-xl` |
| LG | `32rpx` | `$campus-spacing-lg` |
| MD | `24rpx` | `$campus-spacing-md` |
| SM | `16rpx` | `$campus-spacing-sm` |
| XS | `8rpx` | `$campus-spacing-xs` |

## 五、典型组件样式

### 卡片

```scss
background: white;
border-radius: $campus-radius-card;
box-shadow: $campus-shadow-card;
padding: $campus-spacing-md;
```

### 主色按钮

```scss
background: $campus-primary;
color: white;
border-radius: $campus-radius-btn;
```

### 输入框

```scss
background: $campus-primary-light;
border-radius: $campus-radius-input;
```

### 渐变 Header

```scss
background: $campus-gradient;
color: white;
```

### 服务宫格图标

```scss
.icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: $campus-primary;
  color: white;
}
```

## 六、wot-ui 主题接入

主题类名 `wot-theme-campus` 已在 `App.ku.vue` 的 `wd-config-provider` 上设置，全局生效。

wot-ui 组件会自动使用 `campus.scss` 中定义的语义变量（主色、填充色、边框色等）。

## 七、UnoCSS 快捷类

常用原子类对应关系：

| 效果 | UnoCSS 类 |
|------|-----------|
| 卡片圆角 | `rounded-3`（12px） |
| 按钮圆角 | `rounded-2`（8px） |
| 卡片阴影 | `shadow-sm` |
| 页面内边距 | `px-4`（16px） |
| 主色背景 | 用 `style` 绑定 `$campus-primary` |
