/**
 * Typography——排版原语组件
 * @author AaronZZH & Kiro
 *
 * 基于 shadcn/ui 官方 Typography 示例：
 * - 去掉 scroll-m-20（文章锚点，管理后台不需要）
 * - 字号比官方文章示例更紧凑（管理后台密度更高）
 * - 颜色使用语义 token，适配亮/暗主题
 * - H2 保留 border-b，用于页面内分区
 *
 * 参考：https://ui.shadcn.com/docs/components/base/typography
 *
 * @example
 * ```tsx
 * <TypographyH1>消息中心</TypographyH1>
 * <TypographyH2>基本信息</TypographyH2>
 * <TypographyH3>通知设置</TypographyH3>
 * <TypographyMuted>暂无数据</TypographyMuted>
 * <TypographyInlineCode>@aaf/entity-engine</TypographyInlineCode>
 * ```
 */

import { cn } from "@/lib/utils/cn"

/** 页面级主标题——用于页面顶部 */
export function TypographyH1({ className, ...props }: React.ComponentProps<"h1">) {
  return (
    <h1 className={cn("font-bold text-2xl text-foreground tracking-tight", className)} {...props} />
  )
}

/** 区块标题——用于卡片/面板/弹窗，带底部分割线 */
export function TypographyH2({ className, ...props }: React.ComponentProps<"h2">) {
  return (
    <h2
      className={cn(
        "border-b pb-2 font-semibold text-foreground text-xl tracking-tight first:mt-0",
        className
      )}
      {...props}
    />
  )
}

/** 小节标题——用于表单分组、设置项分组 */
export function TypographyH3({ className, ...props }: React.ComponentProps<"h3">) {
  return (
    <h3
      className={cn("font-semibold text-base text-foreground tracking-tight", className)}
      {...props}
    />
  )
}

/** 四级标题——用于列表项标题、卡片内标题 */
export function TypographyH4({ className, ...props }: React.ComponentProps<"h4">) {
  return (
    <h4
      className={cn("font-semibold text-foreground text-sm tracking-tight", className)}
      {...props}
    />
  )
}

/** 正文段落 */
export function TypographyP({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-sm leading-7 [&:not(:first-child)]:mt-4", className)} {...props} />
}

/** 引用块 */
export function TypographyBlockquote({ className, ...props }: React.ComponentProps<"blockquote">) {
  return (
    <blockquote
      className={cn("mt-4 border-l-2 pl-4 text-muted-foreground italic", className)}
      {...props}
    />
  )
}

/** 无序列表 */
export function TypographyList({ className, ...props }: React.ComponentProps<"ul">) {
  return <ul className={cn("my-4 ml-6 list-disc text-sm [&>li]:mt-1.5", className)} {...props} />
}

/** 行内代码 */
export function TypographyInlineCode({ className, ...props }: React.ComponentProps<"code">) {
  return (
    <code
      className={cn(
        "rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono font-semibold text-xs",
        className
      )}
      {...props}
    />
  )
}

/** 导语——用于页面副标题、描述文字 */
export function TypographyLead({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-base text-muted-foreground", className)} {...props} />
}

/** 大号文字——用于数字、强调内容 */
export function TypographyLarge({ className, ...props }: React.ComponentProps<"div">) {
  return <div className={cn("font-semibold text-base", className)} {...props} />
}

/** 小号文字——用于标签、辅助说明 */
export function TypographySmall({ className, ...props }: React.ComponentProps<"small">) {
  return <small className={cn("font-medium text-xs leading-none", className)} {...props} />
}

/** 静音文字——用于占位提示、次要信息 */
export function TypographyMuted({ className, ...props }: React.ComponentProps<"p">) {
  return <p className={cn("text-muted-foreground text-sm", className)} {...props} />
}
