/**
 * Mock /api/upload——文件上传接口
 * 生产环境替换为真实存储（OSS/S3/本地）
 */

import { type NextRequest, NextResponse } from "next/server"

/** 最大上传文件大小：50MB */
const MAX_UPLOAD_SIZE = 50 * 1024 * 1024

export async function POST(req: NextRequest) {
  const form = await req.formData()
  const file = form.get("file") as File | null

  if (!file) {
    return NextResponse.json({ code: 400, message: "缺少文件" }, { status: 400 })
  }

  if (file.size > MAX_UPLOAD_SIZE) {
    return NextResponse.json(
      { code: 413, message: `文件大小超过限制（最大 ${MAX_UPLOAD_SIZE / 1024 / 1024}MB）` },
      { status: 413 }
    )
  }

  // Mock：把文件转为 base64 data URL 返回（生产环境替换为真实存储 URL）
  const buffer = await file.arrayBuffer()
  const base64 = Buffer.from(buffer).toString("base64")
  const dataUrl = `data:${file.type};base64,${base64}`

  return NextResponse.json({
    code: 0,
    data: { url: dataUrl, name: file.name, size: file.size }
  })
}
