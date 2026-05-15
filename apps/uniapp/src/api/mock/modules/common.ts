import { defineMock } from '@alova/mock'
import { generateMockData } from '../utils/generators'

export default defineMock({
  // 通用GET请求处理
  '[GET]/*': (_params: any, matchedUrl: string) => {
    console.log(`[Mock] GET ${matchedUrl}`, _params)
    return generateMockData.baseResponse({
      message: `Mock response for GET ${matchedUrl}`,
      params: _params,
    })
  },

  // 通用POST请求处理
  '[POST]/*': (_params: any, matchedUrl: string) => {
    console.log(`[Mock] POST ${matchedUrl}`, _params)
    return generateMockData.baseResponse({
      message: `Mock response for POST ${matchedUrl}`,
      params: _params,
    })
  },
})
