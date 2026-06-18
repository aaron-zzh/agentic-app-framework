import { AuthGuard } from "@/lib/auth/AuthGuard"

export default function OcrLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>
}
