import { defineMock } from '@alova/mock'
import { generateMockData } from '../utils/generators'

/** 生成单条消息 */
function mockMessage(index: number) {
  const types = ['system', 'chat', 'notice'] as const
  return {
    id: index + 1,
    title: `消息标题 ${index + 1}`,
    content: `这是第 ${index + 1} 条消息的内容，用于演示分页加载效果。`,
    type: types[index % 3],
    read: generateMockData.boolean(),
    createdAt: generateMockData.datetime(-generateMockData.number(0, 30)),
  }
}

/** 总数据量 */
const TOTAL = 35

export default defineMock({
  '[GET]/mock/messages': ({ query }) => {
    const page = Number(query?.page ?? 1)
    const pageSize = Number(query?.pageSize ?? 10)
    const start = (page - 1) * pageSize
    const list = Array.from({ length: TOTAL }, (_, i) => mockMessage(i))
      .slice(start, start + pageSize)

    return generateMockData.listResponse(list, TOTAL)
  },
})
