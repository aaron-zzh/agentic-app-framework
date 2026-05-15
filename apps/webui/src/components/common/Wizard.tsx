/**
 * Wizard——多步骤向导弹窗
 * @author AaronZZH & Kiro
 */

"use client"

import { useCallback, useState } from "react"
import { useForm, FormProvider } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import type { z } from "zod"

/** 步骤定义 */
export interface WizardStep {
  label: string
  description?: string
  schema?: z.ZodType
  content: React.ReactNode
}

interface WizardProps {
  open: boolean
  onClose: () => void
  onComplete: (data: Record<string, unknown>) => void
  steps: WizardStep[]
  title?: string
}

/** 多步骤向导弹窗 */
export function Wizard({ open, onClose, onComplete, steps, title }: WizardProps) {
  const [currentStep, setCurrentStep] = useState(0)
  const step = steps[currentStep]

  const form = useForm({
    resolver: step?.schema ? zodResolver(step.schema) : undefined,
  })

  const handleNext = useCallback(async () => {
    if (step?.schema) {
      const valid = await form.trigger()
      if (!valid) return
    }

    if (currentStep < steps.length - 1) {
      setCurrentStep((s) => s + 1)
    } else {
      onComplete(form.getValues())
      onClose()
      setCurrentStep(0)
      form.reset()
    }
  }, [currentStep, steps.length, step, form, onComplete, onClose])

  const handleBack = useCallback(() => {
    setCurrentStep((s) => Math.max(0, s - 1))
  }, [])

  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} onKeyDown={undefined} />
      <div className="relative w-full max-w-lg rounded-lg border bg-background shadow-xl">
        {/* 标题 */}
        <div className="border-b px-6 py-4">
          <h2 className="text-lg font-semibold">{title ?? "向导"}</h2>
        </div>

        {/* 步骤指示器 */}
        <div className="flex items-center gap-2 border-b px-6 py-3">
          {steps.map((s, i) => (
            <div key={s.label} className="flex items-center gap-2">
              <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${i <= currentStep ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground"}`}>
                {i + 1}
              </span>
              <span className={`text-sm ${i === currentStep ? "font-medium" : "text-muted-foreground"}`}>
                {s.label}
              </span>
              {i < steps.length - 1 && <span className="mx-1 text-muted-foreground">→</span>}
            </div>
          ))}
        </div>

        {/* 内容 */}
        <FormProvider {...form}>
          <div className="min-h-[200px] px-6 py-4">
            {step?.content}
          </div>
        </FormProvider>

        {/* 操作按钮 */}
        <div className="flex justify-between border-t px-6 py-4">
          <button
            type="button"
            className="rounded border px-4 py-2 text-sm disabled:opacity-30"
            onClick={handleBack}
            disabled={currentStep === 0}
          >
            上一步
          </button>
          <button
            type="button"
            className="rounded bg-primary px-4 py-2 text-sm text-primary-foreground"
            onClick={handleNext}
          >
            {currentStep === steps.length - 1 ? "完成" : "下一步"}
          </button>
        </div>
      </div>
    </div>
  )
}
