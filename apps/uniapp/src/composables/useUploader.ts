/**
 * 文件上传 composable
 * 支持：S3 预签名直传 | 服务端上传 | 图片压缩（超 3MB 自动压缩）| 上传进度
 * 参考 kids-app hooks/useNutUploader.ts，去除 nutui 依赖
 */
import { alovaInstance } from '@/api/core/instance'

/** 上传模式：client=S3预签名直传，server=服务端中转 */
type UploadMode = 'client' | 'server'

export interface UploadFile {
  path: string
  name: string
  size?: number
  type?: string
  /** 上传成功后的访问 URL */
  url?: string
}

export interface UploaderOptions {
  /** 上传模式，默认读取环境变量 VITE_UPLOAD_TYPE */
  mode?: UploadMode
  /** 文件路径前缀 */
  prefix?: string
  /** 超过此大小自动压缩（字节），默认 3MB */
  compressThreshold?: number
}

/** 图片压缩 */
async function compressImage(file: UploadFile): Promise<string> {
  return new Promise((resolve) => {
    uni.compressImage({
      src: file.path,
      quality: 85,
      success: res => resolve(res.tempFilePath),
      fail: () => resolve(file.path),
    })
  })
}

/** 读取文件为 ArrayBuffer */
function readFileAsBuffer(filePath: string): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const fs = uni.getFileSystemManager()
    fs.readFile({
      filePath,
      success: res => resolve(new Uint8Array(res.data as ArrayBuffer).buffer),
      fail: err => reject(new Error(err.errMsg)),
    })
  })
}

/** 获取文件大小（字节） */
function getFileSize(filePath: string): Promise<number> {
  return new Promise((resolve) => {
    const fs = uni.getFileSystemManager()
    fs.open({
      filePath,
      success: (res) => {
        fs.fstat({
          fd: res.fd,
          success: stat => resolve(stat.stats.size),
          fail: () => resolve(0),
        })
      },
      fail: () => resolve(0),
    })
  })
}

export function useUploader(options: UploaderOptions = {}) {
  const {
    mode = (import.meta.env.VITE_UPLOAD_TYPE as UploadMode) ?? 'server',
    prefix = '',
    compressThreshold = 3 * 1024 * 1024,
  } = options

  const progress = ref(0)
  const uploading = ref(false)

  /**
   * 上传单个文件
   * @returns 上传成功后的访问 URL
   */
  async function upload(file: UploadFile): Promise<string> {
    uploading.value = true
    progress.value = 0

    try {
      const fileName = prefix ? `${prefix}-${file.name}` : file.name

      if (mode === 'client') {
        return await uploadToS3(file, fileName)
      }
      else {
        return await uploadToServer(file, fileName)
      }
    }
    finally {
      uploading.value = false
    }
  }

  /** S3 预签名直传 */
  async function uploadToS3(file: UploadFile, fileName: string): Promise<string> {
    // 1. 获取预签名 URL
    const presigned = await alovaInstance.Get<{ uploadUrl: string, url: string }>('/infra/file/presigned-url', {
      params: { path: fileName },
    })

    // 2. 图片超限自动压缩
    let filePath = file.path
    const size = await getFileSize(filePath)
    if (file.type?.startsWith('image') && size > compressThreshold) {
      filePath = await compressImage({ ...file, path: filePath })
    }

    // 3. 读取文件为 ArrayBuffer
    const buffer = await readFileAsBuffer(filePath)

    // 4. PUT 上传到 S3
    await new Promise<void>((resolve, reject) => {
      const ext = fileName.substring(fileName.lastIndexOf('.') + 1)
      uni.request({
        url: presigned.uploadUrl,
        method: 'PUT',
        header: { 'Content-Type': `${file.type ?? 'application'}/${ext}` },
        data: buffer,
        timeout: 120000,
        success: () => resolve(),
        fail: err => reject(new Error(err.errMsg)),
      })
    })

    return presigned.url
  }

  /** 服务端中转上传 */
  async function uploadToServer(file: UploadFile, fileName: string): Promise<string> {
    // 图片超限自动压缩
    let filePath = file.path
    const size = await getFileSize(filePath)
    if (file.type?.startsWith('image') && size > compressThreshold) {
      filePath = await compressImage({ ...file, path: filePath })
    }

    return new Promise((resolve, reject) => {
      const task = uni.uploadFile({
        url: `${import.meta.env.VITE_API_BASE_URL}/infra/file/upload`,
        filePath,
        name: 'file',
        header: { Authorization: `Bearer ${useUserStore().token}` },
        formData: { path: fileName },
        success: (res) => {
          try {
            const data = JSON.parse(res.data)
            resolve(data.data ?? data.url)
          }
          catch {
            reject(new Error('上传响应解析失败'))
          }
        },
        fail: err => reject(new Error(err.errMsg)),
      })
      task.onProgressUpdate(({ progress: p }) => {
        progress.value = p
      })
    })
  }

  return { upload, progress, uploading }
}
