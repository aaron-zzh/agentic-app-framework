"use client"

import { useShallow } from "zustand/react/shallow"

import { useCounterStore } from "@/lib/store/providers"

export function Counter() {
  // 方式 1: 只获取单个值 - 不需要 useShallow（基本类型自动优化）
  // const count = useCounterStore((store) => store.count)

  // 方式 2: 获取多个值 - 使用 useShallow 避免每次返回新对象导致重渲染
  // 问题：selector 每次返回新对象 {} !== {}，即使内容相同也会重渲染
  // useShallow 通过浅比较对象属性值，只在实际值变化时才重渲染
  const { count, increment, decrement, reset } = useCounterStore(
    useShallow((store) => ({
      count: store.count,
      increment: store.increment,
      decrement: store.decrement,
      reset: store.reset
    }))
  )
  return (
    <div>
      <h1>
        Count: <span>{count}</span>
      </h1>
      <button type="button" onClick={increment}>
        +1
      </button>
      <button type="button" onClick={decrement}>
        -1
      </button>
      <button type="button" onClick={reset}>
        Reset
      </button>
    </div>
  )
}
