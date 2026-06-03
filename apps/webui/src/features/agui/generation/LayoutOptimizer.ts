/**
 * 布局自动优化引擎
 * 响应式布局生成、间距优化、视觉层次、A/B 方案对比
 * @author AaronZZH & Kiro
 */

import type { DataFieldDef, FieldDef } from "@/lib/types/entity"

/** 布局方案描述（字段名引用，实际渲染时由 ViewEngine 解析为 FieldDef） */
export interface LayoutDescriptor {
  type: "linear" | "tabs" | "row"
  /** tabs 模式下的分组 */
  tabs?: { label: string; fieldNames: string[] }[]
  /** row 模式下的列 */
  columns?: { label: string; fieldNames: string[]; width: string }[]
}

/** 布局方案 */
export interface LayoutProposal {
  id: string
  name: string
  description: string
  /** 表单布局描述 */
  layout: LayoutDescriptor
  /** 列表列配置（字段名列表） */
  listColumns: string[]
  /** 响应式断点配置 */
  responsive: ResponsiveConfig
  /** 评分（0-100） */
  score: number
  /** 评分理由 */
  scoreReasons: string[]
}

/** 响应式配置 */
export interface ResponsiveConfig {
  /** 桌面端列数 */
  desktopColumns: number
  /** 平板端列数 */
  tabletColumns: number
  /** 手机端列数 */
  mobileColumns: number
  /** 字段在不同断点的可见性 */
  fieldVisibility?: Record<string, { desktop: boolean; tablet: boolean; mobile: boolean }>
}

/** 字段重要性权重 */
interface FieldWeight {
  name: string
  weight: number
  reason: string
}

class LayoutOptimizerImpl {
  /** 生成优化布局方案（返回多个供选择） */
  generateProposals(fields: FieldDef[], options?: { maxProposals?: number }): LayoutProposal[] {
    const maxProposals = options?.maxProposals ?? 3
    const weights = this.calculateFieldWeights(fields)
    const proposals: LayoutProposal[] = []

    // 方案 A：紧凑单列布局
    proposals.push(this.buildCompactLayout(fields, weights))

    // 方案 B：分组 Tab 布局
    if (fields.length > 5) {
      proposals.push(this.buildTabbedLayout(fields, weights))
    }

    // 方案 C：双列布局
    if (fields.length > 3) {
      proposals.push(this.buildTwoColumnLayout(fields, weights))
    }

    return proposals.sort((a, b) => b.score - a.score).slice(0, maxProposals)
  }

  /** 优化列表视图列配置 */
  optimizeListColumns(fields: FieldDef[], maxColumns?: number): string[] {
    const max = maxColumns ?? 6
    const weights = this.calculateFieldWeights(fields)

    return weights
      .sort((a, b) => b.weight - a.weight)
      .slice(0, max)
      .map((w) => w.name)
  }

  /** 生成响应式配置 */
  generateResponsive(fields: FieldDef[]): ResponsiveConfig {
    const weights = this.calculateFieldWeights(fields)
    const fieldVisibility: ResponsiveConfig["fieldVisibility"] = {}

    for (const w of weights) {
      fieldVisibility[w.name] = {
        desktop: true,
        tablet: w.weight > 0.3,
        mobile: w.weight > 0.6
      }
    }

    return {
      desktopColumns: fields.length > 8 ? 3 : 2,
      tabletColumns: 2,
      mobileColumns: 1,
      fieldVisibility
    }
  }

  /** 计算字段重要性权重 */
  private calculateFieldWeights(fields: FieldDef[]): FieldWeight[] {
    return fields
      .filter((f): f is DataFieldDef => "name" in f)
      .map((field, index) => {
        let weight = 0
        const reasons: string[] = []

        // 必填字段权重高
        if (field.required) {
          weight += 0.3
          reasons.push("必填")
        }

        // 标题/名称类字段权重高
        if (field.name === "title" || field.name === "name") {
          weight += 0.3
          reasons.push("标题字段")
        }

        // 状态字段权重高
        if (field.type === "select") {
          weight += 0.2
          reasons.push("状态/分类字段")
        }

        // 位置靠前权重高
        weight += Math.max(0, 0.2 - index * 0.02)

        // 只读字段权重低
        if (field.readOnly) {
          weight -= 0.2
          reasons.push("只读")
        }

        return {
          name: field.name,
          weight: Math.max(0, Math.min(1, weight)),
          reason: reasons.join("、")
        }
      })
  }

  /** 紧凑单列布局 */
  private buildCompactLayout(fields: FieldDef[], weights: FieldWeight[]): LayoutProposal {
    const score = fields.length <= 6 ? 85 : 60
    return {
      id: `layout_compact_${Date.now()}`,
      name: "紧凑单列",
      description: "所有字段线性排列，适合字段较少的简单表单",
      layout: { type: "linear" },
      listColumns: weights
        .sort((a, b) => b.weight - a.weight)
        .slice(0, 5)
        .map((w) => w.name),
      responsive: {
        desktopColumns: 1,
        tabletColumns: 1,
        mobileColumns: 1
      },
      score,
      scoreReasons: fields.length <= 6 ? ["字段数量适中，单列布局清晰"] : ["字段较多，单列可能过长"]
    }
  }

  /** 分组 Tab 布局 */
  private buildTabbedLayout(fields: FieldDef[], weights: FieldWeight[]): LayoutProposal {
    const sorted = [...weights].sort((a, b) => b.weight - a.weight)
    const mid = Math.ceil(sorted.length / 2)

    const primaryFields = sorted.slice(0, mid).map((w) => w.name)
    const secondaryFields = sorted.slice(mid).map((w) => w.name)

    return {
      id: `layout_tabbed_${Date.now()}`,
      name: "分组 Tab",
      description: "按重要性分组为多个 Tab，减少视觉负担",
      layout: {
        type: "tabs",
        tabs: [
          { label: "基本信息", fieldNames: primaryFields },
          { label: "详细信息", fieldNames: secondaryFields }
        ]
      },
      listColumns: primaryFields.slice(0, 5),
      responsive: {
        desktopColumns: 2,
        tabletColumns: 1,
        mobileColumns: 1
      },
      score: fields.length > 5 ? 80 : 50,
      scoreReasons:
        fields.length > 5 ? ["字段较多，Tab 分组降低认知负担"] : ["字段较少，Tab 分组不必要"]
    }
  }

  /** 双列布局 */
  private buildTwoColumnLayout(fields: FieldDef[], weights: FieldWeight[]): LayoutProposal {
    const sorted = [...weights].sort((a, b) => b.weight - a.weight)
    const half = Math.ceil(sorted.length / 2)

    const leftFields = sorted.slice(0, half).map((w) => w.name)
    const rightFields = sorted.slice(half).map((w) => w.name)

    return {
      id: `layout_twocol_${Date.now()}`,
      name: "双列布局",
      description: "左侧主要信息，右侧附加信息，充分利用桌面端宽度",
      layout: {
        type: "row",
        columns: [
          { label: "主要信息", fieldNames: leftFields, width: "60%" },
          { label: "附加信息", fieldNames: rightFields, width: "40%" }
        ]
      },
      listColumns: sorted.slice(0, 6).map((w) => w.name),
      responsive: {
        desktopColumns: 2,
        tabletColumns: 2,
        mobileColumns: 1
      },
      score: fields.length >= 4 && fields.length <= 12 ? 75 : 55,
      scoreReasons:
        fields.length >= 4 && fields.length <= 12
          ? ["字段数量适合双列展示"]
          : ["字段数量不太适合双列"]
    }
  }
}

/** 全局单例 */
export const LayoutOptimizer = new LayoutOptimizerImpl()
