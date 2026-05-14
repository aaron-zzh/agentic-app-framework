---
level: Practice
layer: Product
purpose: AAF-036 移动端脚手架的技术任务清单
status: active
version: 1.0.0
date: 2026-05-15
author: AaronZZH
---

# 移动端脚手架（AAF-036）

> 需求：移动端脚手架（详见 [aaf-v0.1.0.md 业务需求](../../aaf-v0.1.0.md#aaf-036移动端脚手架)）
> 设计：[tech-stack.md](../../../design/apps/uniapp/tech-stack.md) | [directory-structure.md](../../../design/apps/uniapp/directory-structure.md) | [mobile-admin.md](../../../design/apps/uniapp/mobile-admin.md)
> 参考：kids-app（SSE/platform/权限拦截）| wot-starter-v2（工程基础）
> 负责人：developer-app | 创建：05-15

## 执行策略

按"工程接入 → 基础设施 → 核心能力 → 业务骨架 → 清理"顺序推进，每个任务独立可验证。

## 任务列表

### 一、工程接入

1. [ ] #1 接入 Nx monorepo — developer-app
   - 删除 `apps/uniapp/pnpm-workspace.yaml`
   - 创建 `apps/uniapp/project.json`，定义 targets：`dev`、`build:mp-weixin`、`build:h5`、`lint`、`type-check`
   - 根目录 `pnpm install` 验证依赖解析
   - verify: `pnpm nx dev uniapp` 可启动 H5 开发服务

2. [ ] #2 补充缺失依赖 — developer-app (依赖: #1)
   - 安装：`@hyoga/uni-socket.io weixin-js-sdk gesti`
   - 安装 dev：`vconsole rollup-plugin-visualizer vite-plugin-compression`
   - 确认 `pinia-plugin-persistedstate` 支持 `uni.storage`，否则换 `pinia-plugin-persist-uni`
   - verify: `pnpm nx run uniapp:type-check` 无类型错误

3. [ ] #3 ESLint 对齐 AAF 规范 — developer-app (依赖: #1)
   - `eslint.config.mjs` 补充：`@typescript-eslint/no-explicit-any: error`、`no-console: warn`
   - verify: `pnpm nx run uniapp:lint` 通过

### 二、基础设施

4. [ ] #4 平台抽象层 — developer-app (依赖: #2)
   - 创建 `src/platform/index.ts`：`name`、`provider`、`checkNetwork()`、`getCapsule()`、`navbar`
   - 创建 `src/platform/provider/weixin/index.ts`：`load()`（JS-SDK 初始化）、`checkUpdate()`
   - 所有 `#ifdef` 只在 `platform/` 内部出现
   - verify: H5 和微信小程序均可调用 `platform.checkNetwork()`

5. [ ] #5 SSE 双端流式通信 — developer-app (依赖: #2)
   - 从 kids-app 移植 `src/request/stream.ts`（`wx.request` + `enableChunked: true`）
   - 从 kids-app 移植 `src/request/stream_h5.ts`（`fetchEventSource`，`#ifdef H5`）
   - 统一 UTF-8 解码、SSE 格式解析、buffer 处理
   - verify: 微信开发者工具和 H5 均可接收流式响应

6. [ ] #6 WebSocket 封装 — developer-app (依赖: #2)
   - 创建 `src/request/websocket.ts`，基于 `@hyoga/uni-socket.io` 封装连接/断开/重连/事件监听
   - verify: 可建立连接并收发消息

7. [ ] #7 应用启动序列 — developer-app (依赖: #4)
   - 创建 `src/store/app.ts`，`init()`：检查网络 → 设置主题 → 检查登录态
   - `App.vue` 的 `onLaunch` 调用 `useAppStore().init()`
   - verify: 冷启动序列按顺序执行，网络异常跳错误页

8. [ ] #8 权限路由拦截 — developer-app (依赖: #7)
   - `src/router/index.ts` 在 `@wot-ui/router` 基础上加 `uni.addInterceptor('navigateTo/redirectTo')`
   - 未登录跳登录页，无权限跳首页，管理端路由校验管理员角色
   - verify: 未登录访问鉴权页自动跳转；普通用户访问 `subPages/admin/` 被拦截

### 三、核心业务骨架

9. [ ] #9 用户状态与认证 — developer-app (依赖: #8)
   - 创建 `src/store/user.ts`：token、用户信息、角色（user/admin）、权限列表、登录/登出
   - 持久化到 `uni.storage`
   - verify: 登录后重启小程序，用户状态保持

10. [ ] #10 alova 请求实例配置 — developer-app (依赖: #9)
    - 完善 `src/api/core/instance.ts`：baseURL 从环境变量读取，token 自动注入，401 自动跳登录
    - 完善 `src/api/core/handlers.ts`：统一解包 `{ code, data, message }` 响应格式
    - verify: 请求 401 时自动清除 token 并跳转登录页

11. [ ] #11 用户端页面骨架 — developer-app (依赖: #9)
    - 创建主包页面：`pages/index/`（首页）、`pages/chat/`（对话列表 + `[id].vue` 详情）、`pages/profile/`（个人中心）
    - 配置 `pages.config.ts` tabbar（首页/对话/我的）
    - verify: 三个 tabbar 页面可正常切换

12. [ ] #12 管理端分包骨架 — developer-app (依赖: #8)
    - 创建 `src/subPages/admin/`：`dashboard/`、`users/`、`audit/`（各含占位页面）
    - `pages.config.ts` 配置 admin 分包
    - 个人中心加入角色切换入口（管理员可见）
    - verify: 管理员可进入管理分包，普通用户被拦截

### 四、AI Skills 迁移与清理

13. [ ] #13 AI Skills 迁移 — developer-app (依赖: #1)
    - 将 `.agents/skills/` 下 4 个 skill 迁移到 `.kiro/skills/`，对齐 AAF Kiro 格式
    - verify: Kiro 可识别并加载 uniapp skill

14. [ ] #14 脚手架 Demo 清理 — developer-app (依赖: #11, #12)
    - 删除 `src/subPages/` 下演示页（router/pinia/request/feedback/skills/ci/create-uni/icon/uni-ku-root）
    - 删除 `src/pages/about/`、`src/components/DemoBlock.vue`
    - 清理 `pages.config.ts` 对应路由
    - verify: `pnpm nx run uniapp:build:mp-weixin` 构建成功，主包 < 2MB

### 五、通用能力补充

15. [ ] #15 启动页与登录流程 — developer-app (依赖: #9)
    - 创建 `pages/startup/index.vue`：检查登录态，决定跳首页或登录页
    - 创建 `pages/login/index.vue`：微信一键登录（`wx.login` + 手机号授权）+ H5 账号密码登录
    - verify: 未登录冷启动跳登录页；登录成功跳首页；token 有效时跳过登录

16. [ ] #16 文件上传 composable — developer-app (依赖: #10)
    - 创建 `src/composables/useUploader.ts`，封装：S3 预签名上传（`uni.request PUT`）、服务端直传（alova uploadFile）、图片压缩（`uni.compressImage`，超 3MB 自动压缩）、上传进度
    - 参考 kids-app `hooks/useNutUploader.ts` 实现，去除 nutui 依赖
    - verify: 图片上传成功，超 3MB 自动压缩后上传

17. [ ] #17 AI 对话消息列表（虚拟列表） — developer-app (依赖: #11)
    - 引入 `z-paging`（uni_modules），实现聊天记录模式（`use-chat-record-mode`）+ 虚拟列表（`use-virtual-list`）
    - 创建 `src/components/chat/ChatBubble.vue`：用户/AI 气泡，AI 消息用 `mp-html` 渲染（支持 HTML 内容）
    - 创建 `src/components/chat/StreamText.vue`：流式文字追加渲染
    - verify: 消息列表 100 条以上滚动流畅，流式内容实时追加

18. [ ] #18 Markdown 渲染 — developer-app (依赖: #17)
    - 引入 `marked`（或复用 uview-plus 内置的 `marked.esm.js`），将 Markdown 转 HTML
    - 在 `ChatBubble.vue` 中 AI 消息走 `marked → mp-html` 渲染链
    - verify: AI 回复中的代码块、列表、加粗、表格正确渲染

19. [ ] #19 Canvas 海报生成与编辑 — developer-app (依赖: #2)
    - 引入 `gesti`（手势 Canvas 库，参考 kids-app `pages/school/print/gesti/`）
    - 创建 `src/components/common/PosterEditor.vue`：可拖拽/缩放/旋转元素（背景图、头像、文字、二维码）
    - 创建 `src/components/common/PosterCanvas.vue`：固定模板快速生成（不需编辑时用）
    - 暴露 `generate()` 返回临时图片路径，支持 `uni.saveImageToPhotosAlbum` 保存
    - verify: 可拖拽调整元素位置，生成海报并保存到相册

20. [ ] #20 消息通知页 — developer-app (依赖: #11)
    - 创建 `subPages/message/list.vue`：消息列表，已读/未读状态，alova usePagination 分页
    - 创建 `subPages/message/detail.vue`：消息详情，mp-html 渲染富文本内容
    - 个人中心加消息入口 + 未读角标
    - verify: 消息列表分页正常，点击跳详情，已读状态更新

21. [ ] #21 用户信息与设置 — developer-app (依赖: #16)
    - 创建 `subPages/profile/edit.vue`：头像上传（useUploader）、昵称、手机号修改
    - 创建 `subPages/settings/index.vue`：通知设置、隐私协议、关于、退出登录、清除缓存
    - verify: 头像上传成功，设置项可正常操作

22. [ ] #22 管理端扩展页面 — developer-app (依赖: #12)
    - 创建 `subPages/admin/notice/`：公告列表 + 发布/编辑（mp-html 渲染）
    - 创建 `subPages/admin/roles/`：角色列表 + 权限配置
    - 创建 `subPages/admin/settings/`：系统配置（KV 表单）
    - verify: 管理员可发布公告，可配置角色权限

23. [ ] #23 多模态图像处理 — developer-app (依赖: #16, #17)
    - 创建 `src/components/chat/ImagePicker.vue`：图片选择 + 预览 + 删除，支持多图
    - 在 `MessageInput.vue` 集成图片选择入口（相册/拍照）
    - 在 `ChatBubble.vue` 支持图片消息渲染（用户发送的图片 + AI 返回的图片）
    - 参考 kids-app `editor/image.vue` 的上传 + AI 处理流程
    - verify: 可选图上传，AI 回复中图片正常展示

24. [ ] #24 Echarts 图表封装 — developer-app (依赖: #12)
    - 在 `subEcharts/` 分包封装常用图表组件：折线图、柱状图、饼图、数据看板卡片
    - 管理端 `admin/dashboard/` 使用图表组件展示数据概览
    - verify: 管理端看板图表正常渲染，分包加载不影响主包体积
    - 创建 `subPages/admin/notice/`：公告列表 + 发布/编辑（富文本，mp-html 渲染）
    - 创建 `subPages/admin/roles/`：角色列表 + 权限配置
    - 创建 `subPages/admin/settings/`：系统配置（KV 表单）
    - verify: 管理员可发布公告，可配置角色权限

- [ ] `pnpm nx dev uniapp` 可启动 H5 开发服务
- [ ] `pnpm nx run uniapp:build:mp-weixin` 构建成功，主包 < 2MB
- [ ] `pnpm nx run uniapp:lint` 通过，无 `any` 类型错误
- [ ] `pnpm nx run uniapp:type-check` 通过
- [ ] 微信小程序和 H5 均可接收 SSE 流式响应
- [ ] 双角色路由拦截正常工作
- [ ] `.kiro/skills/` 包含 4 个 wot-ui skill


## 验收标准

- [ ] `pnpm nx dev uniapp` 可启动 H5 开发服务
- [ ] `pnpm nx run uniapp:build:mp-weixin` 构建成功，主包 < 2MB
- [ ] `pnpm nx run uniapp:lint` 通过，无 `any` 类型错误
- [ ] `pnpm nx run uniapp:type-check` 通过
- [ ] 微信小程序和 H5 均可接收 SSE 流式响应
- [ ] 双角色路由拦截正常工作（普通用户无法进入管理分包）
- [ ] 登录流程完整（微信一键登录 + H5 账号密码）
- [ ] AI 对话消息列表 100 条以上滚动流畅，Markdown 正确渲染
- [ ] 图片上传支持 S3 预签名，超 3MB 自动压缩
- [ ] Canvas 海报可生成并保存到相册
- [ ] 消息通知、用户信息编辑、设置页可正常使用
- [ ] 管理端公告、角色权限页面可正常操作
- [ ] `.kiro/skills/` 包含 4 个 wot-ui skill
