# 插件开发指南

## 插件体系概述

AAF 插件系统支持三种扩展维度：

| 维度 | 说明 | 示例 |
|------|------|------|
| 字段类型 | 注册新的表单/列表字段组件 | 颜色选择器、地图选点、签名板 |
| 视图类型 | 注册新的数据展示视图 | 地图视图、甘特图、日历视图 |
| 批量操作 | 注册列表批量操作 | 批量发邮件、批量导出 |

## 插件包结构

```text
@aaf/plugin-xxx/
├── src/
│   ├── index.ts              → 插件入口（注册逻辑）
│   ├── components/           → 自定义组件
│   │   ├── field.tsx         → 表单字段组件
│   │   └── cell.tsx          → 列表单元格组件
│   └── lib/                  → 纯逻辑
├── package.json
└── tsconfig.json
```

## 注册自定义字段类型

### 前端注册

```typescript
// src/index.ts
import { registerFieldType } from '@aaf/entity-engine';
import { ColorPickerField } from './components/field';
import { ColorSwatchCell } from './components/cell';
import { z } from 'zod';

registerFieldType('colorPicker', {
  fieldComponent: ColorPickerField,
  cellComponent: ColorSwatchCell,
  zodSchema: (field) => z.string().regex(/^#[0-9a-f]{6}$/i),
  defaultProps: { format: 'hex' },
});
```

### 字段组件契约

```tsx
// 表单字段组件 Props
interface FieldProps<T> {
  name: string;
  value: T;
  onChange: (value: T) => void;
  error?: string;
  disabled?: boolean;
  field: FieldDef;
}

// 列表单元格组件 Props
interface CellProps<T> {
  value: T;
  record: Record<string, any>;
  field: FieldDef;
}
```

### 实现示例

```tsx
// components/field.tsx
'use client';

import { FieldProps } from '@aaf/entity-engine';

export function ColorPickerField({ value, onChange, disabled }: FieldProps<string>) {
  return (
    <input
      type="color"
      value={value || '#000000'}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
    />
  );
}
```

## 注册自定义视图类型

```typescript
import { registerViewType } from '@aaf/entity-engine';
import { MapView } from './components/map-view';

registerViewType('map', {
  component: MapView,
  icon: 'map-pin',
  label: '地图视图',
  requiredFields: ['latitude', 'longitude'], // 实体需包含这些字段
});
```

视图组件接收标准 Props：

```tsx
interface ViewProps {
  entity: EntityDef;
  data: Record<string, any>[];
  params: URLSearchParams;
}
```

## 注册批量操作

```typescript
import { registerBatchAction } from '@aaf/entity-engine';

registerBatchAction('sendEmail', {
  label: '发送邮件',
  icon: 'mail',
  visibleFor: ['contact', 'lead'], // 仅特定实体可用
  handler: async (records) => {
    const ids = records.map((r) => r.id);
    await fetch('/api/actions/send-email', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    });
  },
});
```

## 生命周期钩子

```typescript
// 在 EntityDef 中声明钩子
const customerEntity: EntityDef = {
  slug: 'customer',
  hooks: {
    beforeCreate: async (data) => {
      // 自动填充创建时间
      return { ...data, createdAt: new Date().toISOString() };
    },
    afterCreate: async (record) => {
      // 创建后发送通知
      await notifyTeam(record);
    },
    beforeDelete: async (ids) => {
      // 检查是否有关联订单，有则阻止删除
      const hasOrders = await checkOrders(ids);
      return !hasOrders; // 返回 false 阻止删除
    },
  },
};
```

## 后端工具插件

### 注册 MCP 工具

```java
@Component
public class MyPlugin {

    @Tool(description = "发送短信通知")
    public String sendSms(
        @Param(description = "手机号") String phone,
        @Param(description = "内容") String content
    ) {
        // 调用短信服务
        return smsService.send(phone, content);
    }
}
```

## 插件发布

```json
// package.json
{
  "name": "@aaf/plugin-color-picker",
  "version": "1.0.0",
  "peerDependencies": {
    "@aaf/entity-engine": "^1.0"
  },
  "main": "dist/index.js",
  "types": "dist/index.d.ts"
}
```

安装使用：

```bash
pnpm add @aaf/plugin-color-picker
```

在应用入口注册：

```typescript
import '@aaf/plugin-color-picker'; // 自动注册
```
