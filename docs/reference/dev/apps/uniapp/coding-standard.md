---
level: Practice
layer: Model
purpose: AAF 小程序/APP 多端兼容编码规范，开发时查阅
status: draft
version: 0.1.0
date: 2026-05-05
author: AaronZZH
gains:
  - 能正确使用条件编译处理多端差异
  - 能按规范组织页面和组件
---

# 小程序/APP 编码规范（uniapp）

> 当前状态：待开发，本文档为规范占位，开发启动时细化。

## 目录约定

```
pages/          页面（对应路由）
components/     共享组件
store/          Pinia 状态管理
api/            接口封装
utils/          工具函数
static/         静态资源
```

## 条件编译

多端差异通过条件编译处理，不写平台判断逻辑：

```vue
<!-- ✅ 条件编译 -->
<!-- #ifdef MP-WEIXIN -->
<view>微信小程序专属内容</view>
<!-- #endif -->

<!-- #ifdef H5 -->
<div>H5 专属内容</div>
<!-- #endif -->

<!-- ❌ 运行时判断 -->
<view v-if="platform === 'weixin'">...</view>
```

## 多端统一原则

- 业务逻辑写在 `<script setup>` 中，不依赖平台 API
- 平台差异封装到 `utils/platform.ts`，组件不直接调用 `uni.*` 平台特有 API
- 样式使用 `rpx` 单位，不用 `px`

> 技术选型见 uniapp 技术选型
