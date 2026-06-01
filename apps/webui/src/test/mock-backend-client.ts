import { AxiosError, type AxiosAdapter, type AxiosResponse } from "axios"
import { vi } from "vitest"
import { backendClient } from "@/lib/api/rest/backend-client"

type MockResponse = {
  data: unknown
  status?: number
  statusText?: string
}

export const mockBackendRequest = vi.fn()

export function installMockBackendClient(): void {
  backendClient.defaults.adapter = mockBackendAdapter
}

export function resetMockBackendClient(): void {
  mockBackendRequest.mockReset()
  delete backendClient.defaults.headers.common.Authorization
  delete backendClient.defaults.headers.common["X-Org-Id"]
  delete backendClient.defaults.headers.common["X-Workspace-Id"]
}

export function mockBackendResponse(data: unknown, status = 200): void {
  mockBackendRequest.mockResolvedValueOnce({
    data,
    status,
    statusText: status >= 200 && status < 300 ? "OK" : "Error"
  })
}

const mockBackendAdapter: AxiosAdapter = async (config) => {
  const mocked = (await mockBackendRequest(config)) as MockResponse
  const status = mocked.status ?? 200
  const response: AxiosResponse = {
    data: mocked.data,
    status,
    statusText: mocked.statusText ?? (status >= 200 && status < 300 ? "OK" : "Error"),
    headers: {},
    config,
    request: {}
  }
  const validateStatus =
    config.validateStatus ?? ((responseStatus: number) => responseStatus >= 200 && responseStatus < 300)
  if (!validateStatus(status)) {
    throw new AxiosError(
      `Request failed with status code ${status}`,
      AxiosError.ERR_BAD_REQUEST,
      config,
      {},
      response
    )
  }
  return response
}
