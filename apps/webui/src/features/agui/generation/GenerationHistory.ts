/**
 * 生成历史与模板管理
 * 记录生成历史、保存为模板、模板市场、版本管理
 * @author AaronZZH & Kiro
 *
 * 注意：当前为纯内存存储，页面刷新后数据丢失。
 * TODO: 后续接入 localStorage 或后端持久化
 */

import type { EntityDef } from "@/lib/types/entity"

import type { GenerationResult } from "./ComponentGenerator"

/** 历史记录条目 */
export interface HistoryEntry {
  id: string
  /** 用户输入 */
  input: string
  /** 生成结果 */
  output: GenerationResult
  /** 生成时间 */
  timestamp: number
  /** 是否已保存为模板 */
  savedAsTemplate?: string
}

/** 模板定义 */
export interface GenerationTemplate {
  id: string
  /** 模板名称 */
  name: string
  /** 模板描述 */
  description: string
  /** 模板配置 */
  config: Partial<EntityDef>
  /** 创建者 */
  createdBy: string
  /** 创建时间 */
  createdAt: number
  /** 更新时间 */
  updatedAt: number
  /** 是否公开 */
  isPublic: boolean
  /** 使用次数 */
  usageCount: number
  /** 标签 */
  tags: string[]
  /** 版本号 */
  version: number
  /** 版本历史 */
  versions: TemplateVersion[]
}

/** 模板版本 */
export interface TemplateVersion {
  version: number
  config: Partial<EntityDef>
  timestamp: number
  changelog: string
}

/** 模板市场筛选 */
export interface MarketplaceFilter {
  tags?: string[]
  sortBy?: "popular" | "recent" | "name"
  search?: string
}

class GenerationHistoryImpl {
  private history: HistoryEntry[] = []
  private templates = new Map<string, GenerationTemplate>()

  /** 记录生成历史 */
  record(input: string, output: GenerationResult): HistoryEntry {
    const entry: HistoryEntry = {
      id: `hist_${Date.now()}`,
      input,
      output,
      timestamp: Date.now()
    }
    this.history.push(entry)
    // 保留最近 200 条
    if (this.history.length > 200) this.history.shift()
    return entry
  }

  /** 获取历史列表 */
  getHistory(limit?: number): HistoryEntry[] {
    const entries = [...this.history].reverse()
    return limit ? entries.slice(0, limit) : entries
  }

  /** 按实体筛选历史 */
  getHistoryByEntity(entity: string): HistoryEntry[] {
    return this.history.filter((h) => h.output.intent.entity === entity).reverse()
  }

  /** 保存为模板 */
  saveAsTemplate(
    historyId: string,
    meta: {
      name: string
      description: string
      tags: string[]
      isPublic: boolean
      createdBy: string
    }
  ): GenerationTemplate | null {
    const entry = this.history.find((h) => h.id === historyId)
    if (!entry) return null

    const template: GenerationTemplate = {
      id: `tpl_${Date.now()}`,
      name: meta.name,
      description: meta.description,
      config: entry.output.config,
      createdBy: meta.createdBy,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isPublic: meta.isPublic,
      usageCount: 0,
      tags: meta.tags,
      version: 1,
      versions: [
        {
          version: 1,
          config: entry.output.config,
          timestamp: Date.now(),
          changelog: "初始版本"
        }
      ]
    }

    this.templates.set(template.id, template)
    entry.savedAsTemplate = template.id
    return template
  }

  /** 使用模板 */
  useTemplate(templateId: string): Partial<EntityDef> | null {
    const template = this.templates.get(templateId)
    if (!template) return null
    template.usageCount++
    return template.config
  }

  /** 更新模板（生成新版本） */
  updateTemplate(
    templateId: string,
    config: Partial<EntityDef>,
    changelog: string
  ): GenerationTemplate | null {
    const template = this.templates.get(templateId)
    if (!template) return null

    template.version++
    template.config = config
    template.updatedAt = Date.now()
    template.versions.push({
      version: template.version,
      config,
      timestamp: Date.now(),
      changelog
    })

    return template
  }

  /** 回退到指定版本 */
  rollbackTemplate(templateId: string, version: number): GenerationTemplate | null {
    const template = this.templates.get(templateId)
    if (!template) return null

    const targetVersion = template.versions.find((v) => v.version === version)
    if (!targetVersion) return null

    template.config = targetVersion.config
    template.updatedAt = Date.now()
    template.version++
    template.versions.push({
      version: template.version,
      config: targetVersion.config,
      timestamp: Date.now(),
      changelog: `回退到 v${version}`
    })

    return template
  }

  /** 模板市场查询 */
  queryMarketplace(filter?: MarketplaceFilter): GenerationTemplate[] {
    let results = [...this.templates.values()].filter((t) => t.isPublic)

    if (filter?.tags?.length) {
      results = results.filter((t) => filter.tags?.some((tag) => t.tags.includes(tag)))
    }

    if (filter?.search) {
      const q = filter.search.toLowerCase()
      results = results.filter(
        (t) => t.name.toLowerCase().includes(q) || t.description.toLowerCase().includes(q)
      )
    }

    switch (filter?.sortBy) {
      case "popular":
        results.sort((a, b) => b.usageCount - a.usageCount)
        break
      case "recent":
        results.sort((a, b) => b.updatedAt - a.updatedAt)
        break
      case "name":
        results.sort((a, b) => a.name.localeCompare(b.name))
        break
      default:
        results.sort((a, b) => b.usageCount - a.usageCount)
    }

    return results
  }

  /** 获取模板详情 */
  getTemplate(templateId: string): GenerationTemplate | undefined {
    return this.templates.get(templateId)
  }

  /** 获取模板版本历史 */
  getVersionHistory(templateId: string): TemplateVersion[] {
    return this.templates.get(templateId)?.versions ?? []
  }

  /** 删除模板 */
  deleteTemplate(templateId: string): boolean {
    return this.templates.delete(templateId)
  }
}

/** 全局单例 */
export const GenerationHistory = new GenerationHistoryImpl()
