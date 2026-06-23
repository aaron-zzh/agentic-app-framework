/**
 * 会议记录整理 API
 * @author AaronZZH & Kiro
 */

import { backendApi } from "../backend-client"

export interface MeetingOrganizeRequest {
  transcript: string
  modelId?: string
  meetingDate?: string
}

export const meetingApi = {
  organize: (req: MeetingOrganizeRequest) =>
    backendApi.post<{ content: string }>("/tool/meeting/organize", req)
}
