import { useQuery } from "@tanstack/react-query"
import { systemConfigApi } from "@/lib/api/rest/system/config"

/** FAQ 条目类型 */
export interface FaqItem {
  q: string
  a: string
}

/** 按分类查询系统配置 */
export function useSystemConfigs(category: string) {
  return useQuery({
    queryKey: ["system-configs", category],
    queryFn: () => systemConfigApi.listByCategory(category),
    staleTime: 10 * 60 * 1000
  })
}

/** 读取 member.faq，解析为 FaqItem[] */
export function useMemberFaq() {
  const { data: configs, isLoading } = useSystemConfigs("member")
  const faqConfig = configs?.find((c) => c.key === "member.faq")
  let faq: FaqItem[] = []
  if (faqConfig?.value) {
    try {
      faq = JSON.parse(faqConfig.value) as FaqItem[]
    } catch {
      faq = []
    }
  }
  return { faq, isLoading }
}
