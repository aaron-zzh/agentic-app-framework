import { AuthGuard } from "@/lib/auth/AuthGuard"

export default function ImageLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>
}
