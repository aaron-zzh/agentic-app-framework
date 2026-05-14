/**
 * 登录页
 * @author AaronZZH & Kiro
 */

export default function LoginPage() {
  return (
    <div className="w-full space-y-6">
      <div>
        <h2 className="text-2xl font-bold">登录</h2>
        <p className="mt-1 text-sm text-muted-foreground">输入账号密码登录系统</p>
      </div>
      {/* TODO: 接入 Form + Field 组件 */}
      <div className="space-y-4">
        <div className="h-9 rounded-md border bg-muted/30" />
        <div className="h-9 rounded-md border bg-muted/30" />
        <div className="h-9 rounded-md bg-primary" />
      </div>
    </div>
  )
}
