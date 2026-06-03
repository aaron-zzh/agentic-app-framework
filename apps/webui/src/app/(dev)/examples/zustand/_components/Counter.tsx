"use client"

// Zustand Hook 使用模式
// 等价对比:
// - Jotai: const [count, setCount] = useAtom(countAtom)
// - Pinia: const store = useCounterStore(); store.count++; // 直接修改！
// - Redux: const count = useSelector(state => state.count); const dispatch = useDispatch()
//
// 状态修改方式对比:
// Zustand: 必须通过 set() 方法 - set((state) => ({ count: state.count + 1 }))
// Jotai: 必须通过 setter - setCount(count + 1) 或 setCount(c => c + 1)
// Pinia: 直接修改 - store.count++ 或 store.count = 10 (Vue 响应式)
// Redux: 必须 dispatch action - dispatch({ type: 'INCREMENT' })
//
// 直接修改对比:
// ❌ Zustand: count++ (不会触发更新)
// ❌ Jotai: count++ (不会触发更新)
// ✅ Pinia: store.count++ (自动响应式更新)
// ❌ Redux: state.count++ (违反不可变原则)
import { useEffect } from "react"
import { create } from "zustand"

type Store = {
  count: number
  inc: () => void
}

// Zustand Store 定义 - 每个 create() 创建一个独立的全局 store
// 等价于:
// - Jotai: const countAtom = atom(1) - 每个 atom 是独立的状态单元
// - Pinia: defineStore('counter', { ... }) - 每个 defineStore 创建一个独立的 store
//
// 组织方式对比:
// Zustand: 多个独立 store (useCounterStore, useUserStore, useCartStore)
// Pinia: 多个独立 store (useCounterStore(), useUserStore(), useCartStore())
// Redux: 单一 store + 多个 slice (store.getState().counter, store.getState().user)
const useStore = create<Store>()((set) => ({
  count: 1,
  inc: () => set((state) => ({ count: state.count + 1 }))
}))

export default function Counter() {
  // Hook 调用 - 自动订阅状态变化
  // 等价于:
  // - Jotai: const [count] = useAtom(countAtom); const inc = useSetAtom(incAtom)
  // - Pinia: const { count, inc } = storeToRefs(useCounterStore())
  const { count, inc } = useStore()

  useEffect(() => {
    // 手动订阅 store 变化
    const unsubscribe = useStore.subscribe((_state) => {})

    // 清理订阅
    return unsubscribe
    // console.log(`Count changed to: ${count}`)
  }, [])

  return (
    <div className="absolute top-[-100px] -right-5 h-[120px] w-[120px] rounded-[10px] bg-slate-600 p-10 text-[13px] text-white shadow-[0_16px_40px_-5px_rgba(0,0,0,0.5)] max-md:top-[-120px]">
      <span className="absolute top-1/2 left-1/2 mt-[-15px] -translate-x-1/2 -translate-y-1/2 text-[48px]">
        {count}
      </span>
      <button
        type="button"
        className="absolute bottom-0 left-0 m-[10px] w-[100px] cursor-pointer rounded-[5px] border-2 border-white border-solid bg-transparent px-[10px] py-[5px] text-white outline-none"
        onClick={inc}
      >
        one up
      </button>
    </div>
  )
}
