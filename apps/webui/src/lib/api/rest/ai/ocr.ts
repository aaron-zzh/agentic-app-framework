/**
 * OCR 识别 API
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export interface OcrRecognizeRequest {
  imageUrl: string
  task?: string
  imageWidth?: number
  imageHeight?: number
}

export interface OcrRecognizeResult {
  text: string
  ocrResult: string | null
  inputTokens: number
  outputTokens: number
}

export const ocrApi = {
  recognize: (req: OcrRecognizeRequest) =>
    backendApi.post<OcrRecognizeResult>("/ai/ocr/recognize", req)
}
