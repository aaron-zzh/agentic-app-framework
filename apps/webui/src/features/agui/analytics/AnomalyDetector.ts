/**
 * 异常行为检测器
 * 检测失败率突增、异常路径、重复失败等异常模式
 * @author AaronZZH & Kiro
 */

import type { AnomalyEvent, UserAction } from "../types"

/** 异常检测时间窗口（毫秒） */
const DETECTION_WINDOW = 60000

/** 失败率阈值 */
const ERROR_RATE_THRESHOLD = 0.3

/** 重复失败阈值 */
const REPEATED_FAILURE_THRESHOLD = 3

class AnomalyDetectorImpl {
  private actions: UserAction[] = []
  private anomalies: AnomalyEvent[] = []
  private errorCounts = new Map<string, { total: number; errors: number; timestamps: number[] }>()

  /** 记录操作（含成功/失败标记） */
  recordAction(action: UserAction, success = true): void {
    this.actions.push(action)
    if (this.actions.length > 500) {
      this.actions = this.actions.slice(-500)
    }

    // 更新错误计数
    const key = `${action.type}:${action.target}`
    const stats = this.errorCounts.get(key) ?? { total: 0, errors: 0, timestamps: [] }
    stats.total++
    if (!success) {
      stats.errors++
      stats.timestamps.push(action.timestamp)
    }
    this.errorCounts.set(key, stats)

    this.detect(action, success)
  }

  /** 获取检测到的异常 */
  getAnomalies(since?: number): AnomalyEvent[] {
    if (since) return this.anomalies.filter((a) => a.timestamp >= since)
    return this.anomalies
  }

  /** 清空 */
  reset(): void {
    this.actions = []
    this.anomalies = []
    this.errorCounts.clear()
  }

  private detect(action: UserAction, success: boolean): void {
    const now = Date.now()

    // 检测失败率突增
    const key = `${action.type}:${action.target}`
    const stats = this.errorCounts.get(key)
    if (stats && stats.total >= 5) {
      const recentErrors = stats.timestamps.filter((t) => now - t < DETECTION_WINDOW).length
      const recentTotal = this.actions.filter(
        (a) => `${a.type}:${a.target}` === key && now - a.timestamp < DETECTION_WINDOW
      ).length
      if (recentTotal > 0 && recentErrors / recentTotal > ERROR_RATE_THRESHOLD) {
        this.addAnomaly({
          type: "error_spike",
          severity: "high",
          description: `操作 "${action.target}" 失败率突增（${recentErrors}/${recentTotal}）`,
          timestamp: now,
          context: { target: action.target, errorRate: recentErrors / recentTotal }
        })
      }
    }

    // 检测重复失败
    if (!success && stats) {
      const recentFailures = stats.timestamps.filter((t) => now - t < 30000).length
      if (recentFailures >= REPEATED_FAILURE_THRESHOLD) {
        this.addAnomaly({
          type: "repeated_failure",
          severity: "medium",
          description: `操作 "${action.target}" 30 秒内连续失败 ${recentFailures} 次`,
          timestamp: now,
          context: { target: action.target, failureCount: recentFailures }
        })
      }
    }

    // 检测异常路径（非常规导航序列）
    if (action.type === "navigate") {
      const recentNavigations = this.actions.filter(
        (a) => a.type === "navigate" && now - a.timestamp < 10000
      )
      if (recentNavigations.length >= 5) {
        this.addAnomaly({
          type: "unusual_path",
          severity: "low",
          description: "10 秒内频繁导航切换，用户可能迷失",
          timestamp: now,
          context: { navigationCount: recentNavigations.length }
        })
      }
    }
  }

  private addAnomaly(anomaly: AnomalyEvent): void {
    // 去重：同类型异常 10 秒内不重复记录
    const recent = this.anomalies.find(
      (a) => a.type === anomaly.type && Date.now() - a.timestamp < 10000
    )
    if (!recent) {
      this.anomalies.push(anomaly)
      // 保留最近 100 条
      if (this.anomalies.length > 100) {
        this.anomalies = this.anomalies.slice(-100)
      }
    }
  }
}

/** 全局单例 */
export const AnomalyDetector = new AnomalyDetectorImpl()
