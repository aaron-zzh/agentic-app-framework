import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
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
