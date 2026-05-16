import AdapterUniapp from '@alova/adapter-uniapp'
import { createAlova } from 'alova'
import vueHook from 'alova/vue'
import mockAdapter from '../mock/mockAdapter'
import { handleAlovaError, handleAlovaResponse } from './handlers'

export const alovaInstance = createAlova({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  ...AdapterUniapp({
    mockRequest: mockAdapter,
  }),
  statesHook: vueHook,
  beforeRequest: (method) => {
    // 自动注入 token
    const token = useUserStore().token
    if (token) {
      method.config.headers.Authorization = `Bearer ${token}`
    }

    // POST/PUT/PATCH 设置 Content-Type
    if (['POST', 'PUT', 'PATCH'].includes(method.type)) {
      method.config.headers['Content-Type'] = 'application/json'
    }
  },

  responded: {
    onSuccess: handleAlovaResponse,
    onError: handleAlovaError,
  },

  timeout: 60000,
  cacheFor: null,
})

export default alovaInstance
