"use client"

import { useEffect } from "react"
import { create } from "zustand"
import { useShallow } from "zustand/react/shallow"

type BearFamilyMealsStore = {
  [key: string]: string
}

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
  const names = useBearFamilyMealsStore(useShallow((state) => Object.keys(state)))

  return <div>{names.join(", ")}</div>
}

export default function App() {
  return (
    <>
      <UpdateBabyBearMeal />
      <BearNames />
    </>
  )
}
