---
level: Practice
layer: Product
purpose: AAF-045 LiveChatter 的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# LiveChatter（AAF-045）

> 设计：[chat-livechat-module.md](../../../design/apps/webui/chat-livechat-module.md)
> 负责人：developer-webui | 创建：05-19

## 任务列表

1. [ ] #4501 对话面板布局
   - 侧边栏对话列表、主区域消息流、输入区
   - 响应式布局（桌面/平板/移动）
   - 对话面板可嵌入任意页面（Drawer/Panel 模式）
   - verify: 布局在各尺寸下正常显示

2. [ ] #4502 消息渲染
   - Markdown 渲染（remark/rehype）
   - 代码高亮（Shiki）、代码块复制
   - 数学公式（KaTeX）、表格渲染
   - verify: 复杂 Markdown 内容正确渲染

3. [ ] #4503 文件预览与附件
   - 图片预览（lightbox）、PDF 预览
   - 文件上传拖拽、粘贴上传
   - 附件列表展示
   - verify: 文件上传→预览流程通过

4. [ ] #4504 对话分支
   - 消息编辑重新生成（分支）
   - 分支切换（同一位置多个回复）
   - 分支历史可视化
   - verify: 编辑消息后生成新分支可切换

5. [ ] #4505 对话导出
   - 导出为 Markdown/PDF/JSON
   - 选择性导出（选中消息）
   - 分享链接生成
   - verify: 导出文件内容完整可读
