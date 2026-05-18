import { useCallback, useState } from "react"

/**
 * Tab 状态管理 Hook，适配 shadcn/ui Tabs 的 onValueChange 接口
 *
 * @example
 * const tabs = useTabs('overview');
 * <Tabs value={tabs.value} onValueChange={tabs.onChange}>
 *   <TabsList>
 *     <TabsTrigger value="overview">概览</TabsTrigger>
 *   </TabsList>
 * </Tabs>
 */
export function useTabs(defaultValue: string) {
  const [value, setValue] = useState(defaultValue)

  const onChange = useCallback((newValue: string) => {
    setValue(newValue)
  }, [])

  return { value, onChange, setValue }
}
