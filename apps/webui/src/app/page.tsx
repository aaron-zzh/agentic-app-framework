import { Button } from "@/components/ui/button"

export default function HomePage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4">
      <h1 className="font-bold text-4xl">AAF</h1>
      <p className="text-muted-foreground">Agentic App Framework</p>
      <Button>开始使用</Button>
    </main>
  )
}
