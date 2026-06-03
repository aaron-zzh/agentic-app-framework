"use client"

import { useEffect } from "react"
import { create } from "zustand"
import { useShallow } from "zustand/react/shallow"

type BearFamilyMealsStore = {
  [key: string]: string
}

// 示例 store：三只熊分别对应自己的餐食描述。
const useBearFamilyMealsStore = create<BearFamilyMealsStore>()(() => ({
  papaBear: "large porridge-pot",
  mamaBear: "middle-size porridge pot",
  babyBear: "A little, small, wee pot"
}))

const meals = [
  "A tiny, little, wee bowl",
  "A small, petite, tiny pot",
  "A wee, itty-bitty, small bowl",
  "A little, petite, tiny dish",
  "A tiny, small, wee vessel",
  "A small, little, wee cauldron",
  "A little, tiny, small cup",
  "A wee, small, little jar",
  "A tiny, wee, small pan",
  "A small, wee, little crock"
]

function UpdateBabyBearMeal() {
  useEffect(() => {
    // 每秒只更新 babyBear 的餐食，用来模拟 store 中部分状态频繁变化。
    const timer = setInterval(() => {
      useBearFamilyMealsStore.setState({
        babyBear: meals[Math.floor(Math.random() * (meals.length - 1))]
      })
    }, 1000)

    return () => {
      clearInterval(timer)
    }
  }, [])

  return null
}

function BearNames() {
  // const names = useBearFamilyMealsStore((state) => Object.keys(state))
  // useShallow 会浅比较 selector 返回的数组，避免仅因新数组引用导致重渲染。
  const names = useBearFamilyMealsStore(useShallow((state) => Object.keys(state)))

  return <div>{names.join(", ")}</div>
}

export default function App() {
  return (
    <>
      <UpdateBabyBearMeal />
      <p>这个示例每秒更新 babyBear 的餐食，但页面只订阅熊的名字列表。</p>
      <BearNames />
    </>
  )
}
