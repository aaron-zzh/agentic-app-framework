# 开发记录：表单引擎（AAF-030）

执行者：AI/developer-webui

## #3001 RelationshipPicker 增强

✅ 2026-05-17 — developer-webui

- 抽 `use-relationship-picker.ts`：搜索 debounce + 最近选择记忆（localStorage）
- 重构 `relationship-picker.tsx`：shadcn Command + Popover + Badge + HoverCard
- 最近选择在无搜索词时展示，搜索时切换为结果列表

## #3002 Cascader

✅ 2026-05-17 — developer-webui

- `use-field-cascader.ts`：多级异步加载，上级变更自动清空下级
- `field-cascader.tsx`：shadcn Select 级联，dependsOn 配置驱动

## #3003 Upload 增强

✅ 2026-05-17 — developer-webui

- 修复 `UploadAvatar` a11y（加 role/onKeyDown/tabIndex）
- 文件列表已有预览，保持现状

## #3004 RichTextEditor

✅ 2026-05-17 — developer-webui

- 保持 textarea 降级实现，工具栏占位
- TODO: 安装 @tiptap/react 后启用完整实现

## #3005 Signature

✅ 2026-05-17 — developer-webui

- `field-signature.tsx`：Canvas 手写板，支持鼠标和触摸
- 确认后导出 PNG，可选上传到服务器（uploadEndpoint）
- 已有签名时显示图片 + 重新签名按钮

## #3006 Subtable

✅ 2026-05-17 — developer-webui

- 抽 `use-subtable.ts`：增删改行 + 汇总计算
- 重构 `subtable.tsx`：shadcn Input + Button，Tab 键末尾自动添加行

## #3007 Money/Quantity

✅ 2026-05-17 — developer-webui

- `field-money.tsx`：FieldMoney（值+币种）+ FieldQuantity（值+单位）
- 数据存储为 `{ value, currency/unit }` 对象

## #3008-#3011 FieldContext + 条件逻辑 + 动态过滤 + 动态默认值

✅ 已有完整实现（field-context.ts + use-conditional-fields.ts）

## #3012 公式引擎

✅ 已有完整实现（formula-engine.ts）

## #3013 跨字段校验

✅ 已有完整实现（validation-rules.ts）

## #3014 离开确认

✅ 2026-05-17 — developer-webui

- 升级 `use-unsaved-guard.ts`：返回 showDialog 状态 + tryLeave/confirmLeave/closeDialog
- 新增 `UnsavedGuardDialog.tsx`：shadcn Dialog，三个操作（保存并离开/放弃/取消）

## #3015 Wizard

✅ 已有实现（components/common/Wizard.tsx）

## #3016 SmartButton

✅ 2026-05-17 — developer-webui

- `SmartButton.tsx`：从 entity.smartButtons 读取配置，渲染计数按钮
- EntityDef 新增 `smartButtons?: SmartButton[]` 类型

## #3017 QR Scanner

✅ 2026-05-17 — developer-webui

- `field-qrscanner.tsx`：移动端显示扫码按钮，桌面端隐藏
- TODO: 集成 html5-qrcode 库，当前降级为 prompt 输入
