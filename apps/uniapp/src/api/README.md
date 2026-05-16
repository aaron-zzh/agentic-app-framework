# API 层说明

## 目录结构

```
api/
├── core/               # alova 实例配置（不要改）
│   ├── instance.ts     # alova 实例 + token 注入
│   ├── handlers.ts     # 统一响应/错误处理
│   └── middleware.ts   # 请求中间件
├── mock/               # 开发阶段 mock 数据
│   ├── mockAdapter.ts  # mock 适配器（注册所有 mock 模块）
│   ├── modules/        # 按业务域拆分的 mock 定义
│   └── utils/          # mock 数据生成工具
├── apiDefinitions.ts   # ⚠️ 自动生成，禁止手动修改
├── createApis.ts       # ⚠️ 自动生成，禁止手动修改
├── globals.d.ts        # ⚠️ 自动生成，禁止手动修改
├── index.ts            # 统一导出入口
├── chat.ts             # 手写：AAF 后端接口（AI 对话）
└── message.ts          # 手写：mock 接口（消息分页演示）
```

## 何时用哪种方式

### 1. alova gen 生成（`Apis.xxx.xxx()`）

**适用场景**：后端有 OpenAPI/Swagger 文档时。

```ts
// 自动生成，类型安全，有 IDE 提示
const { data } = useRequest(Apis.pet.findPetsByStatus({ params: { status: 'available' } }))
```

**使用方式**：修改 `alova.config.ts` 的 `input` 为后端 OpenAPI 地址，运行：
```bash
pnpm alova-gen
```

`apiDefinitions.ts`、`createApis.ts`、`globals.d.ts` 三个文件会自动重新生成，**不要手动修改**。

---

### 2. 手写 API 文件（`api/xxx.ts`）

**适用场景**：
- 后端接口已知但 OpenAPI 文档未就绪
- 需要对接口做特殊封装（如流式请求）
- 临时 mock 接口的类型定义

```ts
// api/chat.ts
export function getChatMessagePage(params: { conversationId: number, pageNo: number, pageSize: number }) {
  return alovaInstance.Get<{ list: ChatMessage[], total: number }>('/ai/chat/message/my-page', { params })
}

// 使用
const { data } = useRequest(getChatMessagePage({ conversationId: 1, pageNo: 1, pageSize: 20 }))
```

**命名规范**：按业务域命名，如 `chat.ts`、`agent.ts`、`user.ts`。

---

### 3. mock 模块（`mock/modules/xxx.ts`）

**适用场景**：后端接口未就绪，需要前端先行开发。

```ts
// mock/modules/message.ts
export default defineMock({
  '[GET]/mock/messages': ({ query }) => {
    // 返回模拟数据
  },
})
```

新增 mock 模块后，在 `mock/mockAdapter.ts` 中注册。

**注意**：mock 路径建议加 `/mock/` 前缀，避免与真实接口冲突。

---

## 对接 AAF 后端的迁移路径

1. 将 `alova.config.ts` 的 `input` 改为 AAF 后端 OpenAPI 地址
2. 运行 `pnpm alova-gen` 重新生成接口定义
3. 将手写的 `chat.ts`、`message.ts` 等逐步替换为生成的 `Apis.xxx.xxx()` 调用
4. 删除对应的 mock 模块
