"use client"

/**
 * Zustand 示例用 store providers
 * 演示 Context + Zustand 的 per-instance store 模式
 */

import { createContext, type ReactNode, useContext, useRef } from "react"
import { createStore, useStore } from "zustand"

// ─── Counter Store ────────────────────────────────────────────────────────────

interface CounterState {
  count: number
  increment: () => void
  decrement: () => void
  reset: () => void
}

type CounterStore = ReturnType<typeof createCounterStore>

function createCounterStore(init: { count: number }) {
  return createStore<CounterState>()((set) => ({
    count: init.count,
    increment: () => set((s) => ({ count: s.count + 1 })),
    decrement: () => set((s) => ({ count: s.count - 1 })),
    reset: () => set({ count: init.count })
  }))
}

const CounterStoreContext = createContext<CounterStore | null>(null)

export function CounterStoreProvider({ count, children }: { count: number; children: ReactNode }) {
  const store = useRef(createCounterStore({ count })).current
  return <CounterStoreContext value={store}>{children}</CounterStoreContext>
}

export function useCounterStore<T>(selector: (state: CounterState) => T): T {
  const store = useContext(CounterStoreContext)
  if (!store) throw new Error("Missing CounterStoreProvider")
  return useStore(store, selector)
}

// ─── Clock Store ──────────────────────────────────────────────────────────────

interface ClockState {
  lastUpdate: number
  light: boolean
  tick: (ts: number) => void
  toggleLight: () => void
}

type ClockStore = ReturnType<typeof createClockStore>

function createClockStore(init: { lastUpdate: number }) {
  return createStore<ClockState>()((set) => ({
    lastUpdate: init.lastUpdate,
    light: false,
    tick: (ts) => set({ lastUpdate: ts }),
    toggleLight: () => set((s) => ({ light: !s.light }))
  }))
}

export const ClockStoreContext = createContext<ClockStore | null>(null)

export function ClockStoreProvider({
  lastUpdate,
  children
}: {
  lastUpdate: number
  children: ReactNode
}) {
  const store = useRef(createClockStore({ lastUpdate })).current
  return <ClockStoreContext value={store}>{children}</ClockStoreContext>
}

export function useClockStore<T>(selector: (state: ClockState) => T): T {
  const store = useContext(ClockStoreContext)
  if (!store) throw new Error("Missing ClockStoreProvider")
  return useStore(store, selector)
}
