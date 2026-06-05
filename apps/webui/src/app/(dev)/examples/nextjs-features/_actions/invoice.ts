/**
 * 特性演示：Server Actions
 *
 * 'use server' 指令：文件内所有导出函数均为服务端操作。
 * 客户端调用时 Next.js 自动加密函数引用，通过 POST 请求触发。
 *
 * useActionState + State：
 * - State 类型承载验证错误信息，通过 React 状态传回客户端
 * - formData：原生 FormData，无需额外序列化
 * - revalidatePath：清除路由缓存，触发页面重新渲染
 * - redirect：服务端重定向，避免客户端 JS 执行
 */
"use server"

import { revalidatePath } from "next/cache"
import { redirect } from "next/navigation"

import { INVOICES } from "../_data/mock"

const BASE_PATH = "/dev/examples/nextjs-features/dashboard/invoices"

export type State = {
  errors?: { customerId?: string[]; amount?: string[]; status?: string[] }
  message?: string | null
}

export async function createInvoice(_prevState: State, formData: FormData): Promise<State> {
  // 服务端验证（客户端无法绕过）
  const customerId = formData.get("customerId")?.toString()
  const amount = formData.get("amount")?.toString()
  const status = formData.get("status")?.toString()

  const errors: State["errors"] = {}
  if (!customerId) errors.customerId = ["请选择客户"]
  if (!amount || Number.isNaN(Number(amount))) errors.amount = ["请输入有效金额"]
  if (!status) errors.status = ["请选择状态"]

  if (Object.keys(errors).length > 0) {
    return { errors, message: "请填写完整信息" }
  }

  // Mock：实际项目中在此写入数据库
  // console.log("创建发票:", { customerId, amount, status })
  revalidatePath(BASE_PATH) // 清除缓存
  redirect(BASE_PATH) // 服务端重定向
}

export async function updateInvoice(_id: string, _formData: FormData): Promise<void> {
  // console.log("更新发票:", id, Object.fromEntries(formData))
  revalidatePath(BASE_PATH)
  redirect(BASE_PATH)
}

export async function deleteInvoice(id: string): Promise<void> {
  // Mock：实际项目中删除数据库记录
  const index = INVOICES.findIndex((i) => i.id === id)
  if (index > -1) INVOICES.splice(index, 1)
  revalidatePath(BASE_PATH)
}
