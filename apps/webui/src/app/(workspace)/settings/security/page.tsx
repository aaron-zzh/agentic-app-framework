/**
 * 安全设置——修改密码，对标 minimal-ui AccountChangePassword
 * @author AaronZZH
 */

"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { Eye, EyeOff, Info } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useChangePassword } from "@/lib/api/rest/user/profile"
import { notify } from "@/lib/notification"

const schema = z
  .object({
    oldPassword: z.string().min(1, "请输入当前密码").min(6, "密码至少 6 位"),
    newPassword: z.string().min(6, "新密码至少 6 位"),
    confirmPassword: z.string().min(1, "请确认新密码")
  })
  .refine((v) => v.oldPassword !== v.newPassword, {
    message: "新密码不能与当前密码相同",
    path: ["newPassword"]
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "两次输入的密码不一致",
    path: ["confirmPassword"]
  })

type FormValues = z.infer<typeof schema>

function PwdField({
  id,
  label,
  show,
  error,
  helperText,
  registration
}: {
  id: string
  label: string
  show: boolean
  error?: string
  helperText?: React.ReactNode
  registration: ReturnType<ReturnType<typeof useForm<FormValues>>["register"]>
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} type={show ? "text" : "password"} aria-invalid={!!error} {...registration} />
      {helperText && !error && (
        <p className="flex items-center gap-1 text-muted-foreground text-xs">{helperText}</p>
      )}
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

export default function SecurityPage() {
  const [showPwd, setShowPwd] = useState(false)
  const changePassword = useChangePassword()

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting }
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { oldPassword: "", newPassword: "", confirmPassword: "" }
  })

  const onSubmit = handleSubmit(async (data) => {
    try {
      await changePassword.mutateAsync({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword
      })
      reset()
      notify.success("密码修改成功")
    } catch {
      notify.error("密码修改失败，请检查当前密码是否正确")
    }
  })

  const ToggleBtn = () => (
    <button
      type="button"
      className="absolute top-1/2 right-3 -translate-y-1/2 text-muted-foreground"
      onClick={() => setShowPwd((v) => !v)}
      tabIndex={-1}
    >
      {showPwd ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
    </button>
  )

  return (
    <div className="p-6">
      <form onSubmit={onSubmit}>
        <Card className="max-w-lg">
          <CardHeader>
            <CardTitle>修改密码</CardTitle>
            <CardDescription>定期修改密码保障账户安全</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="relative">
              <PwdField
                id="oldPassword"
                label="当前密码"
                show={showPwd}
                error={errors.oldPassword?.message}
                registration={register("oldPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="relative">
              <PwdField
                id="newPassword"
                label="新密码"
                show={showPwd}
                error={errors.newPassword?.message}
                helperText={
                  <>
                    <Info className="size-3.5" />
                    密码至少 6 位字符
                  </>
                }
                registration={register("newPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="relative">
              <PwdField
                id="confirmPassword"
                label="确认新密码"
                show={showPwd}
                error={errors.confirmPassword?.message}
                registration={register("confirmPassword")}
              />
              <ToggleBtn />
            </div>

            <div className="flex justify-end pt-2">
              <Button type="submit" disabled={isSubmitting || changePassword.isPending}>
                {isSubmitting ? "修改中..." : "保存修改"}
              </Button>
            </div>
          </CardContent>
        </Card>
      </form>
    </div>
  )
}
