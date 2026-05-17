/**
 * /api/upload/presign——获取 OSS 预签名上传 URL
 * 生产环境对接真实 OSS（阿里云/AWS S3），当前为 Mock 实现
 */

import { type NextRequest, NextResponse } from "next/server"

export async function POST(req: NextRequest) {
  const { filename, contentType } = await req.json()

  if (!filename || !contentType) {
    return NextResponse.json(
      { code: 400, message: "缺少 filename 或 contentType" },
      { status: 400 }
    )
  }

  // TODO: 生产环境替换为真实 OSS SDK 生成预签名 URL
  // 示例：阿里云 OSS / AWS S3 presigned PUT
  const key = `uploads/${Date.now()}-${filename}`
  const mockUploadUrl = `/api/upload?key=${encodeURIComponent(key)}`
  const mockAccessUrl = `/api/upload/file/${encodeURIComponent(key)}`

  return NextResponse.json({
    code: 0,
    data: {
      uploadUrl: mockUploadUrl,
      accessUrl: mockAccessUrl,
      key
    }
  })
}
