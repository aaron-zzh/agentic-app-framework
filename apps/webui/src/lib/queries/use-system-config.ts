import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { systemConfigApi } from "@/lib/api/rest/system/config"

/** FAQ 条目类型 */
export interface FaqItem {
  q: string
  a: string
}

/**
 * 会员与积分 FAQ 默认内容（system config 为空时使用）
 *
 * <p>结构参考 flova.ai 价格页 FAQ，并适配 AAF 的积分模型：
 * - 积分批次：SUBSCRIPTION/TOPUP/WEEKLY/REWARD/MANUAL
 * - 套餐层级：FREE/PRO/TEAM/ENTERPRISE
 * - 退款联系邮箱：service@xuejiai.com
 *
 * <p>多行答案使用 \n 分隔，由前端通过 whitespace-pre-line 还原换行。
 */
export const DEFAULT_MEMBER_FAQ: FaqItem[] = [
  {
    q: "什么是积分（credits），我如何获得？",
    a: "积分是 AAF 平台的标准计量单位。当你使用 AI 模型对话、图像 / 视频生成、知识库检索、工作流执行等功能时，系统会根据所使用的模型类型、调用次数、Token 消耗、生成时长、分辨率等参数自动扣除相应积分。\n\n你可以通过以下方式获取积分：\n• 订阅获取（Subscription Credits）：订阅会员套餐后，每月可获得固定额度积分，有效期 30 天\n• 充值获取（Top-up Credits）：在「积分详情」页通过订单充值获得，永久有效（除非另有说明）\n• 每周积分（Weekly Credits）：每周一 00:01 自动刷新，有效期 7 天\n• 邀请奖励积分（Invite Bonus Credits）：成功邀请用户注册后获取，有效期 30 天\n• 活动奖励积分（Event Bonus Credits）：参与社区计划或运营活动获得，发放数量与有效期以活动规则为准\n\n⚠️ 积分规则、奖励政策及相关活动机制可能根据运营需要进行调整，调整可在提前通知或不提前通知的情况下进行。在法律允许的范围内，AAF 保留相关规则的最终解释权。"
  },
  {
    q: "积分在使用过程中如何扣除？",
    a: "积分计费规则：积分的具体消耗以「积分详情」页中的模型与计费规则为准，不同模型、不同分辨率、不同生成时长所消耗的积分不同。\n\n积分扣除顺序：系统将优先扣除更快到期的积分，以最大程度保障你的积分使用权益。\n\n异常退还：若因系统问题导致执行失败，系统将自动退还相应积分，无需手动申请。\n\n⚠️ 免费体验期间将启用防刷与防自动化滥用机制，相关使用规则可能根据平台稳定性与公平性需要进行动态调整。"
  },
  {
    q: "订阅是如何运作的？",
    a: "AAF 提供灵活的月度与年度订阅方案，每个方案都包含一定数量的积分，可用于对话、图像生成、视频生成、知识库检索、工作流执行等功能。\n\n当你升级订阅时：\n• 旧套餐仅按已使用积分比例计费\n• 剩余未使用余额将自动抵扣至新套餐\n• 你仅需支付补齐差价\n• 新的订阅周期将从升级当日重新计算"
  },
  {
    q: "订阅会自动续费吗？",
    a: "会的。订阅将在每个计费周期结束时自动续费，除非你在续费日前主动取消。"
  },
  {
    q: "限时优惠活动如何生效？",
    a: "在促销期间订阅，优惠权益将立即生效；\n若你取消订阅，相关促销权益将同步失效；\n如果你在同一促销期内重新订阅，剩余优惠权益将在符合规则的情况下予以恢复。"
  },
  {
    q: "如何修改或取消订阅？",
    a: "你可以随时进行升级：FREE → PRO → TEAM → ENTERPRISE，按月付费 → 按年付费。\n\n取消订阅方式：\n1. 进入「设置 → 价格套餐」\n2. 点击「管理订阅」\n3. 选择「取消订阅」\n\n取消后，你仍可在当前订阅周期内继续使用订阅权益；周期结束后订阅将自动失效，并不再进行自动续费。"
  },
  {
    q: "我如何申请退款？",
    a: "如果你在最近一次付款后未有任何积分消耗记录（包括对话、图像 / 视频生成、知识库检索、工作流执行等），可在购买后 7 天内申请全额退款。\n\n若因系统问题导致执行失败，我们将自动进行相应积分退还，无需手动申请。\n\n如需申请退款，请联系 service@xuejiai.com。退款通常会在 5–10 个工作日内退回原支付方式。"
  }
]

/** 按分类查询系统配置 */
export function useSystemConfigs(category: string) {
  return useQuery({
    queryKey: ["system-configs", category],
    queryFn: () => systemConfigApi.listByCategory(category),
    staleTime: 10 * 60 * 1000
  })
}

/** 查询全部系统配置（管理后台用） */
export function useAllSystemConfigs() {
  return useQuery({
    queryKey: ["system-configs", "*"],
    queryFn: () => systemConfigApi.listAll(),
    staleTime: 5 * 60 * 1000
  })
}

/** 更新系统配置 */
export function useUpdateSystemConfig() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ key, value }: { key: string; value: string }) =>
      systemConfigApi.update(key, value),
    onSuccess: () => {
      // 清除所有分类缓存
      queryClient.invalidateQueries({ queryKey: ["system-configs"] })
    }
  })
}

/** 读取 member.faq，解析为 FaqItem[]；为空或解析失败时返回内置默认 FAQ */
export function useMemberFaq() {
  const { data: configs, isLoading } = useSystemConfigs("member")
  const faqConfig = configs?.find((c) => c.key === "member.faq")
  let faq: FaqItem[] = []
  if (faqConfig?.value) {
    try {
      const parsed = JSON.parse(faqConfig.value) as FaqItem[]
      if (Array.isArray(parsed) && parsed.length > 0) {
        faq = parsed
      }
    } catch {
      faq = []
    }
  }
  // 系统配置未提供有效 FAQ 时回退到默认内容，保证页面始终有可读信息
  if (faq.length === 0 && !isLoading) {
    faq = DEFAULT_MEMBER_FAQ
  }
  return { faq, isLoading }
}
