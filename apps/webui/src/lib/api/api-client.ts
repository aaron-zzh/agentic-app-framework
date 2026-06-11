/**
 * API 客户端基础抽象。
 *
 * 按协议分化：基础 ApiClient 只负责 axios 能力，RestApiClient/GraphqlClient
 * 分别处理 REST 和 GraphQL 的响应语义。
 */

import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse
} from "axios"
import { ApiError } from "./errors"
import type { ApiResult, GraphqlResponse } from "./types"

const DEFAULT_TIMEOUT = 30_000

const defaultAxiosConfig: AxiosRequestConfig = {
  headers: {
    "Content-Type": "application/json",
    Accept: "application/json"
  },
  timeout: DEFAULT_TIMEOUT,
  validateStatus: (status) => status >= 200 && status < 300
}

export class ApiClient {
  protected readonly instance: AxiosInstance

  constructor(baseURL: string, config: AxiosRequestConfig = {}) {
    this.instance = axios.create({
      ...defaultAxiosConfig,
      ...config,
      headers: {
        ...defaultAxiosConfig.headers,
        ...config.headers
      },
      baseURL
    })
    this.setupInterceptors()
  }

  protected setupInterceptors(): void {
    this.instance.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => Promise.reject(error)
    )
  }

  normalizeError(error: AxiosError): ApiError {
    const response = error.response
    if (response) {
      return new ApiError(response.status, `请求失败: ${response.statusText}`)
    }
    return new ApiError(0, error.message || "网络请求失败")
  }

  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: "GET", url })
  }

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: "POST", url, data })
  }

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: "PUT", url, data })
  }

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: "DELETE", url })
  }

  async request<T>(config: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.request<T>(config)
    return response.data
  }

  setHeader(name: string, value: string | null): void {
    if (value) {
      this.instance.defaults.headers.common[name] = value
    } else {
      delete this.instance.defaults.headers.common[name]
    }
  }

  getInstance(): AxiosInstance {
    return this.instance
  }
}

export class RestApiClient extends ApiClient {
  protected override setupInterceptors(): void {
    this.instance.interceptors.response.use(
      (response) => response,
      (error: AxiosError) => Promise.reject(this.normalizeError(error))
    )
  }

  override async request<T>(config: AxiosRequestConfig): Promise<T> {
    const response: AxiosResponse<ApiResult<T>> = await this.instance.request<ApiResult<T>>(config)
    const result = response.data
    if (result.code !== 0) {
      throw new ApiError(result.code, result.message ?? "未知错误")
    }
    return result.data
  }

  override normalizeError(error: AxiosError<ApiResult<unknown>>): ApiError {
    const response = error.response
    if (response?.data?.message) {
      return new ApiError(response.data.code ?? response.status, response.data.message)
    }
    return super.normalizeError(error)
  }
}

export class GraphqlClient extends ApiClient {
  async query<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
    const response = await this.post<GraphqlResponse<T>>("", { query, variables })
    if (response.errors?.length) {
      throw new ApiError(0, response.errors.map((error) => error.message).join("; "))
    }
    if (!response.data) {
      throw new ApiError(0, "GraphQL 响应缺少 data")
    }
    return response.data
  }
}
