---
level: Practice
layer: Product
purpose: AAF-030 表单引擎的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-13
author: AaronZZH
---

# 表单引擎（AAF-030）

> 设计：[结构化交互模式设计](../../../design/apps/webui/interaction-mode-structured-view.md) 章节十八、十九、二十五~二十八、三十二、三十六、五十、五十一、五十六、六十、六十一
> 负责人：architect + developer-web | 创建：05-13

## 任务列表

> **执行策略**：先完善字段组件集，再建条件逻辑和表达式引擎，最后实现高级表单能力。
> 前置：AAF-028 #9（FormView 基础）+ #13（基础字段组件）完成。

### 高级字段组件

1. [ ] #1 关联字段组件（RelationshipPicker） — developer-web
   - 异步搜索下拉（debounce 300ms）
   - 最近选择记忆
   - 快速创建（弹出简化表单）
   - hasMany 时：多选 Tag 模式 + 拖拽排序
   - HoverCard 预览关联记录摘要
   - verify: 搜索/选择/快速创建/多选全链路正确

2. [ ] #2 级联选择组件（Cascader） — developer-web (依赖: #1)
   - 多级关联（省→市→区模式）
   - `dependsOn` 配置级联依赖
   - 上级变更自动清空下级 + 重新加载选项
   - verify: 选择省份后城市列表正确过滤

3. [ ] #3 文件上传组件（Upload） — developer-web
   - 拖拽上传 + 点击选择
   - 前端校验（类型/大小/数量）
   - 上传进度条
   - 图片预览（lightbox）+ 缩略图
   - 图片裁剪（cropper）
   - verify: 拖拽图片上传成功，预览/裁剪正确

4. [ ] #4 富文本编辑器组件（RichText） — developer-web
   - 基于 Tiptap 实现
   - 工具栏：标题/粗体/斜体/链接/图片/代码块/引用/表格
   - 图片粘贴/拖拽上传
   - verify: 富文本编辑保存后内容正确渲染

5. [ ] #5 签名字段组件（Signature） — developer-web
   - Canvas 手写板（signature_pad 库）
   - 支持触摸和鼠标
   - [清除] [确认签名] 操作
   - 签名导出为 PNG → 上传 → 存 fileId + 时间戳
   - 移动端全屏横屏模式
   - verify: 手写签名保存后可查看签名图片

6. [ ] #6 子表明细行组件（Subtable） — developer-web (依赖: #1)
   - 内联表格：增删改行 + 行拖拽排序
   - 汇总行（sum/avg/count）
   - Tab 键横向移动，末尾 Tab 自动添加新行
   - 行删除标记（灰色+删除线），保存时才真正删除
   - 数据作为嵌套数组随父表单提交
   - verify: 添加/删除/排序行正确，汇总实时计算

7. [ ] #7 多币种/单位字段组件 — developer-web
   - Money 字段：值 + 币种选择器
   - Quantity 字段：值 + 单位选择器
   - 显示换算值（showConverted）
   - 数据存储为 `{ value, currency/unit }`
   - verify: 输入金额选择币种后正确存储和展示

### 条件逻辑与表达式

8. [ ] #8 FieldContext 统一表达式上下文 — developer-web
   - 实现 `buildFieldContext(form, user, params)` 构建上下文
   - 实现 `resolveValue(expr, ctx)` 路径解析器（$record / $user / $parent / $params / $env）
   - 安全求值（无 eval，纯路径解析）
   - verify: `$record.status` / `$user.role` / `$parent.id` 正确解析

9. [ ] #9 条件可见性引擎 — developer-web (依赖: #8)
   - `visibleWhen` / `readOnlyWhen` / `requiredWhen` 实时计算
   - 监听 `form.watch()` 变化触发重算
   - 条件不满足时 DOM 不渲染（非 display:none）
   - Zod Schema 动态重建（requiredWhen 切换 optional ↔ required）
   - verify: 状态切换后字段正确显示/隐藏/只读

10. [ ] #10 动态关联过滤（dynamicFilter） — developer-web (依赖: #8, #1)
    - `optionsFrom.dynamicFilter` 引用 $record 字段值
    - watch 被引用字段变化 → 自动重新请求选项
    - queryKey 包含依赖值确保缓存隔离
    - verify: 选择省份后城市下拉自动过滤

11. [ ] #11 动态默认值 — developer-web (依赖: #8)
    - `defaultValue` 支持 `$user.id` / `$user.department` 等表达式
    - 新建记录时自动解析并填入
    - verify: 新建记录时 assignee 自动填入当前用户

### 公式与校验

12. [ ] #12 公式字段引擎 — developer-web (依赖: #8)
    - `type: 'formula'` 字段实时计算
    - 表达式解析器：算术 + IF + 聚合（SUM/AVG）+ 日期函数 + 文本函数
    - 依赖字段变化 → 实时重算 → 只读展示
    - UI：灰底 + fx 图标 + Tooltip 显示公式
    - verify: `price * quantity` 实时计算正确

13. [ ] #13 跨字段校验规则 — developer-web (依赖: #8)
    - `entity.validationRules` 配置解析
    - 校验层次：单字段 Zod → 跨字段规则 → 后端规则
    - error 级别阻止提交，warning 级别仅提示
    - 错误显示在表单顶部 + 关联字段高亮
    - verify: "结束日期>开始日期" 规则校验失败时正确提示

### 表单交互增强

14. [ ] #14 离开确认（Unsaved Changes Guard） — developer-web
    - 监听 `form.formState.isDirty`
    - autosave 开启：自动保存后跳转
    - autosave 关闭：弹出确认对话框（保存并离开/放弃/取消）
    - beforeunload 浏览器关闭拦截
    - verify: 有未保存修改时路由切换弹出确认

15. [ ] #15 向导弹窗（Wizard） — developer-web
    - Dialog + Stepper 多步骤表单
    - 从 `entity.wizards` 配置读取步骤定义
    - 每步独立校验，[上一步] [下一步/完成]
    - verify: 三步向导流程正确执行

16. [ ] #16 Smart Button — developer-web (依赖: AAF-029 #1)
    - 从 `entity.smartButtons` 配置读取
    - 后端返回计数字段 → 渲染为 FormHeader 下方按钮
    - 点击跳转到关联实体列表（带筛选条件）
    - verify: 显示"5 条评论"按钮，点击跳转正确

17. [ ] #17 二维码扫描 — developer-web
    - 移动端输入框右侧扫码图标
    - html5-qrcode 库调用摄像头
    - 识别结果填入 targetField
    - 桌面端隐藏扫码图标
    - verify: 移动端扫码后字段正确填入

<!-- 状态标记：[ ] 待开始 | ⏳ 进行中 | ✅ 已完成 -->
