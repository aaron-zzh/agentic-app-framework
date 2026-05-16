# API Mock 数据

本目录包含了项目中使用的 API 模拟数据，用于开发和测试环境。

## 目录结构

```
mock/
├── modules/          # 按业务域分类的 mock 定义
│   ├── common.ts     # 通用兜底处理（匹配所有未定义路径）
│   ├── message.ts    # 消息列表分页（演示 usePagination）
│   ├── pet.ts        # petstore 宠物接口（演示 alova gen）
│   ├── store.ts      # petstore 商店接口
│   └── user.ts       # petstore 用户接口
├── utils/
│   └── generators.ts # mock 数据生成工具函数
├── mockAdapter.ts    # mock 适配器（注册所有模块，全局拦截）
└── README.md
```

## 请求拦截原理

```
页面调用 API 方法
    ↓
alovaInstance 发起请求
    ↓
mockAdapter 按路径匹配（matchMode: 'pathname'）
    ↓
命中 mock 定义 → 直接返回模拟数据（不发网络请求）
未命中         → 走 uniappRequestAdapter 发真实请求
```

`mockAdapter.ts` 中 `enable: true` 全局开启，所有匹配到 mock 定义的路径都会被拦截。

## 添加新的 mock

1. 在 `modules/` 下新建或修改模块文件：

```ts
// modules/example.ts
import { defineMock } from '@alova/mock'
import { generateMockData } from '../utils/generators'

export default defineMock({
  // GET 列表（支持分页参数）
  '[GET]/mock/example': ({ query }) => {
    const page = Number(query?.page ?? 1)
    const pageSize = Number(query?.pageSize ?? 10)
    const total = 35
    const list = Array.from({ length: total }, (_, i) => ({ id: i + 1, name: `项目${i + 1}` }))
      .slice((page - 1) * pageSize, page * pageSize)
    return generateMockData.listResponse(list, total)
  },

  // POST 创建
  '[POST]/mock/example': ({ data }) => {
    return generateMockData.baseResponse({ id: generateMockData.id(), ...data })
  },
})
```

2. 在 `mockAdapter.ts` 中注册：

```ts
import exampleMocks from './modules/example'

const allMocks = [
  // ...已有模块
  exampleMocks,
]
```

> mock 路径建议加 `/mock/` 前缀，避免与真实接口路径冲突。

## mock 数据生成工具

`utils/generators.ts` 提供常用生成函数：

```ts
import { generateMockData } from '../utils/generators'

generateMockData.id()                          // 随机 ID
generateMockData.name('前缀')                  // 随机名称
generateMockData.number(1, 100)                // 随机数字
generateMockData.boolean()                     // 随机布尔
generateMockData.datetime(-7)                  // 7天前的时间字符串
generateMockData.array(i => ({ id: i }), 10)  // 生成长度为 10 的数组
generateMockData.baseResponse(data)            // { code: 2000, data, msg: '操作成功' }
generateMockData.listResponse(list, total)     // { code: 2000, data: list, total, msg }
```

## 启用/禁用 mock

在 `mockAdapter.ts` 中修改 `enable`：

```ts
const mockAdapter = createAlovaMockAdapter(allMocks, {
  enable: true,   // false 则所有请求走真实接口
  delay: 300,     // 模拟网络延迟（ms）
})
```
