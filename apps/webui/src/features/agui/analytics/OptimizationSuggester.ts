/**
 * UI/UX 优化建议生成器
 * 基于行为数据分析生成优化建议
 * @author AaronZZH & Kiro
 */

import type { OptimizationSuggestion } from "../types"
import { AnomalyDetector } from "./AnomalyDetector"
import { HeatmapCollector } from "./HeatmapCollector"
import { PatternDetector } from "./PatternDetector"

class OptimizationSuggesterImpl {
  /** 生成优化建议 */
  suggest(): OptimizationSuggestion[] {
    const suggestions: OptimizationSuggestion[] = []

    // 基于高频模式建议快捷操作
    const patterns = PatternDetector.getPatterns()
    for (const pattern of patterns.slice(0, 5)) {
      if (pattern.frequency >= 10) {
        suggestions.push({
          type: "shortcut",
          target: pattern.sequence.join(" → "),
          description: `操作序列 "${pattern.sequence.join(" → ")}" 频繁出现（${pattern.frequency} 次），建议添加快捷方式`,
          confidence: Math.min(pattern.frequency / 20, 0.95),
          evidence: [`频率: ${pattern.frequency}`, `平均耗时: ${Math.round(pattern.avgDuration)}ms`]
        })
      }
    }

    // 基于重复操作建议自动化
    const repeated = PatternDetector.getRepeatedActions()
    for (const item of repeated) {
      suggestions.push({
        type: "automate",
        target: item.target,
        description: `操作 "${item.target}" 短时间内重复 ${item.count} 次，建议提供批量操作`,
        confidence: 0.7,
        evidence: [`重复次数: ${item.count}`]
      })
    }

    // 基于热力图建议布局优化
    const hotspots = HeatmapCollector.getHotspots(3)
    const coldspots = HeatmapCollector.getHeatmap().slice(-3)
    if (hotspots.length > 0 && coldspots.length > 0) {
      const hotTotal = hotspots.reduce((s, p) => s + p.count, 0)
      const coldTotal = coldspots.reduce((s, p) => s + p.count, 0)
      if (hotTotal > 0 && coldTotal === 0) {
        suggestions.push({
          type: "reorder",
          target: "layout",
          description: "部分区域点击频率极高而其他区域几乎无交互，建议调整布局优先级",
          confidence: 0.6,
          evidence: [
            `热点: ${hotspots.map((h) => h.target).join(", ")}`,
            `冷区: ${coldspots.map((c) => c.target).join(", ")}`
          ]
        })
      }
    }

    // 基于异常检测建议简化
    const anomalies = AnomalyDetector.getAnomalies(Date.now() - 300000)
    const failureAnomalies = anomalies.filter((a) => a.type === "repeated_failure")
    for (const anomaly of failureAnomalies) {
      suggestions.push({
        type: "simplify",
        target: String(anomaly.context["target"] ?? "unknown"),
        description: `操作 "${anomaly.context["target"]}" 频繁失败，建议简化交互流程或增加引导`,
        confidence: 0.8,
        evidence: [anomaly.description]
      })
    }

    return suggestions.sort((a, b) => b.confidence - a.confidence)
  }
}

/** 全局单例 */
export const OptimizationSuggester = new OptimizationSuggesterImpl()
