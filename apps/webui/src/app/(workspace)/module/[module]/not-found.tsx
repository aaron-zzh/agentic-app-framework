export default function NotFound() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 text-muted-foreground">
      <p className="font-medium text-lg">实体未注册</p>
      <p className="text-sm">请检查 URL 路径是否正确</p>
    </div>
  )
}
