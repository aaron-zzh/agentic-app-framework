import { ClockStoreProvider } from "@/lib/store/providers"

export default function ClockLayout({ children }: { children: React.ReactNode }) {
  return <ClockStoreProvider lastUpdate={Date.now()}>{children}</ClockStoreProvider>
}
