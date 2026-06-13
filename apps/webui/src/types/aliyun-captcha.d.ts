/** 阿里云 ESA AI 验证码全局类型声明 */

interface AliyunCaptchaOptions {
  SceneId: string
  mode: "popup" | "embed"
  element: string
  button: string
  success: (captchaVerifyParam: string) => void
  fail?: (result: unknown) => void
  getInstance?: (instance: unknown) => void
  server?: string[]
  slideStyle?: { width: number; height: number }
  language?: string
  onError?: (errorInfo: { code: string; msg: string }) => void
  onClose?: () => void
}

interface Window {
  AliyunCaptchaConfig?: { region: string; prefix: string }
  initAliyunCaptcha?: (options: AliyunCaptchaOptions) => void
  __aliyunCaptchaInstance?: { refresh?: () => void } | unknown
}
