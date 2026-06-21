/**
 * 系统参数配置页（Odoo 风格）
 * 左侧分类导航，右侧按分类展示配置项，按 value_type 映射不同控件。
 * @author AaronZZH & Kiro
 */

"use client"

import { useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import type { SystemConfigVO } from "@/lib/api/rest/system/config"
import { useAllSystemConfigs, useUpdateSystemConfig } from "@/lib/queries/use-system-config"

// ── 分类元数据 ──────────────────────────────────────────────

const CATEGORY_META: Record<string, { label: string; icon: string }> = {
  site: { label: "站点", icon: "🌐" },
  user: { label: "用户", icon: "👤" },
  security: { label: "安全", icon: "🔒" },
  ai: { label: "AI", icon: "🤖" },
  sms: { label: "短信", icon: "💬" },
  storage: { label: "存储", icon: "🗄️" },
  aigc: { label: "AIGC", icon: "🎨" },
  member: { label: "会员", icon: "⭐" }
}

// ── 单个配置项控件 ───────────────────────────────────────────

interface ConfigFieldProps {
  config: SystemConfigVO
  draft: string
  onChange: (val: string) => void
}

function ConfigField({ config, draft, onChange }: ConfigFieldProps) {
  const { valueType, visible, editable } = config
  const disabled = !editable

  if (valueType === "boolean") {
    return (
      <Switch
        checked={draft === "true"}
        onCheckedChange={(checked) => onChange(String(checked))}
        disabled={disabled}
      />
    )
  }

  if (valueType === "json") {
    return (
      <Textarea
        value={draft}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        rows={4}
        className="font-mono text-xs"
        placeholder="{}"
      />
    )
  }

  // string / integer，敏感字段用密码框
  return (
    <Input
      type={!visible ? "password" : valueType === "integer" ? "number" : "text"}
      value={draft}
      onChange={(e) => onChange(e.target.value)}
      disabled={disabled}
      className="max-w-sm"
    />
  )
}

// ── 分类配置卡片 ─────────────────────────────────────────────

interface CategorySectionProps {
  configs: SystemConfigVO[]
  drafts: Record<string, string>
  onDraftChange: (key: string, val: string) => void
  onSave: (key: string) => void
  isSaving: boolean
}

/** 判断是否为短配置（两列布局） */
function isShort(cfg: SystemConfigVO) {
  return cfg.valueType === "boolean" || cfg.valueType === "integer"
}

function ConfigItem({
  cfg,
  drafts,
  onDraftChange,
  onSave,
  isSaving
}: {
  cfg: SystemConfigVO
  drafts: Record<string, string>
  onDraftChange: (key: string, val: string) => void
  onSave: (key: string) => void
  isSaving: boolean
}) {
  const draft = drafts[cfg.key] ?? ""
  const current = cfg.value ?? cfg.defaultValue ?? ""
  const dirty = draft !== current

  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="mb-2">
        <div className="font-medium text-sm">{cfg.name}</div>
        {cfg.description && (
          <div className="mt-0.5 text-muted-foreground text-xs">{cfg.description}</div>
        )}
        <div className="mt-0.5 font-mono text-muted-foreground/50 text-xs">{cfg.key}</div>
      </div>
      <div className="flex items-center gap-2">
        <ConfigField config={cfg} draft={draft} onChange={(val) => onDraftChange(cfg.key, val)} />
        {cfg.editable && cfg.valueType !== "boolean" && dirty && (
          <Button size="sm" onClick={() => onSave(cfg.key)} disabled={isSaving}>
            保存
          </Button>
        )}
      </div>
    </div>
  )
}

function CategorySection({
  configs,
  drafts,
  onDraftChange,
  onSave,
  isSaving
}: CategorySectionProps) {
  const shortConfigs = configs.filter(isShort)
  const longConfigs = configs.filter((c) => !isShort(c))

  return (
    <div className="space-y-3">
      {/* 短配置：两列网格 */}
      {shortConfigs.length > 0 && (
        <div className="grid grid-cols-2 gap-3">
          {shortConfigs.map((cfg) => (
            <ConfigItem
              key={cfg.key}
              cfg={cfg}
              drafts={drafts}
              onDraftChange={onDraftChange}
              onSave={onSave}
              isSaving={isSaving}
            />
          ))}
        </div>
      )}
      {/* 长配置：单列全宽 */}
      {longConfigs.length > 0 && (
        <div className="space-y-3">
          {longConfigs.map((cfg) => (
            <ConfigItem
              key={cfg.key}
              cfg={cfg}
              drafts={drafts}
              onDraftChange={onDraftChange}
              onSave={onSave}
              isSaving={isSaving}
            />
          ))}
        </div>
      )}
    </div>
  )
}

// ── 主组件 ───────────────────────────────────────────────────

export function SystemConfigSettings() {
  const { data: configs = [], isLoading } = useAllSystemConfigs()
  const { mutate: updateConfig, isPending } = useUpdateSystemConfig()

  // 以 category 为维度分组
  const grouped = configs.reduce<Record<string, SystemConfigVO[]>>((acc, cfg) => {
    if (!acc[cfg.category]) acc[cfg.category] = []
    acc[cfg.category].push(cfg)
    return acc
  }, {})

  const categories = Object.keys(grouped).sort(
    (a, b) => Object.keys(CATEGORY_META).indexOf(a) - Object.keys(CATEGORY_META).indexOf(b)
  )

  const [activeCategory, setActiveCategory] = useState<string>("")
  const currentCategory = activeCategory || categories[0] || ""

  // 草稿状态：key → 当前编辑值
  const [drafts, setDrafts] = useState<Record<string, string>>({})

  // 初始化草稿（仅在首次加载后）
  if (configs.length > 0 && Object.keys(drafts).length === 0) {
    const initial: Record<string, string> = {}
    configs.forEach((cfg) => {
      initial[cfg.key] = cfg.value ?? cfg.defaultValue ?? ""
    })
    setDrafts(initial)
  }

  const handleDraftChange = (key: string, val: string) => {
    setDrafts((prev) => ({ ...prev, [key]: val }))
  }

  const handleSave = (key: string) => {
    const val = drafts[key] ?? ""
    updateConfig(
      { key, value: val },
      {
        onSuccess: () => toast.success("已保存"),
        onError: () => toast.error("保存失败")
      }
    )
  }

  // boolean 切换时自动保存
  const handleBooleanChange = (key: string, val: string) => {
    handleDraftChange(key, val)
    updateConfig(
      { key, value: val },
      {
        onSuccess: () => toast.success("已保存"),
        onError: () => {
          // 回滚
          handleDraftChange(key, val === "true" ? "false" : "true")
          toast.error("保存失败")
        }
      }
    )
  }

  if (isLoading) {
    return <div className="py-20 text-center text-muted-foreground">加载中…</div>
  }

  return (
    <div className="flex h-full gap-0">
      {/* 左侧分类导航 */}
      <nav className="w-52 shrink-0 border-r pr-4">
        <ul className="space-y-0.5">
          {categories.map((cat) => {
            const meta = CATEGORY_META[cat] ?? { label: cat, icon: "⚙️" }
            const isActive = cat === currentCategory
            return (
              <li key={cat}>
                <button
                  type="button"
                  onClick={() => setActiveCategory(cat)}
                  className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors ${
                    isActive
                      ? "bg-primary text-primary-foreground"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  }`}
                >
                  <span>{meta.icon}</span>
                  <span>{meta.label}</span>
                  <span className="ml-auto text-xs opacity-60">{grouped[cat]?.length ?? 0}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </nav>

      {/* 右侧配置内容 */}
      <main className="min-w-0 flex-1 pl-8">
        {currentCategory && grouped[currentCategory] && (
          <>
            <div className="mb-6">
              <h2 className="font-semibold text-lg">
                {CATEGORY_META[currentCategory]?.icon}{" "}
                {CATEGORY_META[currentCategory]?.label ?? currentCategory} 配置
              </h2>
            </div>
            <CategorySection
              configs={grouped[currentCategory]}
              drafts={drafts}
              onDraftChange={(key, val) => {
                const cfg = configs.find((c) => c.key === key)
                if (cfg?.valueType === "boolean") {
                  handleBooleanChange(key, val)
                } else {
                  handleDraftChange(key, val)
                }
              }}
              onSave={handleSave}
              isSaving={isPending}
            />
          </>
        )}
      </main>
    </div>
  )
}
