import { CounterStoreProvider } from "@/lib/store/providers"

export default function CounterLayout({ children }: { children: React.ReactNode }) {
  return <CounterStoreProvider count={0}>{children}</CounterStoreProvider>
}
