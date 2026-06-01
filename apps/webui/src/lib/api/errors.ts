/** API 错误类型 */
export class ApiError extends Error {
  constructor(
    public code: number,
    message: string
  ) {
    super(message)
    this.name = "ApiError"
  }
}

