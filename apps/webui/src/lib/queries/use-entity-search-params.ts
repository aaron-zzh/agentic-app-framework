/**
 * useEntitySearchParams——客户端 URL 状态 Hook
 * @author AaronZZH & Kiro
 */

"use client"

import { useQueryStates } from "nuqs"

import { searchParamsParsers } from "./search-params"

/** 客户端读写 URL 参数 */
export function useEntitySearchParams() {
  return useQueryStates(searchParamsParsers, { shallow: false })
}
