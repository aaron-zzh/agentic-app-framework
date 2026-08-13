/**
 * 实体详情扩展注册表
 *
 * EntityDef 由服务端 JSON 下发，不能保存 React 组件；本地通过 slug 将少数复杂实体
 * 的详情扩展与标准表单组合，避免污染通用实体引擎。
 *
 * @author AaronZZH & Kiro
 */

import type { ReactNode } from "react"
import { BrokerageRulePlanRatesCard } from "@/features/brokerage/components/BrokerageRulePlanRatesCard"

/** 实体详情扩展映射：实体 slug → 渲染函数。 */
const recordDetailExtras: Record<string, (recordId: string, readOnly: boolean) => ReactNode> = {
  "brokerage-rule": (recordId, readOnly) => (
    <BrokerageRulePlanRatesCard ruleId={recordId} readOnly={readOnly} />
  )
}

/** 获取指定实体详情的附加内容；不存在时返回 null。 */
export function getRecordDetailExtra(
  slug: string,
  recordId: string,
  readOnly: boolean
): ReactNode | null {
  const factory = recordDetailExtras[slug]
  return factory ? factory(recordId, readOnly) : null
}
