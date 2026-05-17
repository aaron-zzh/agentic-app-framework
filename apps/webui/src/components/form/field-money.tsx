/**
 * FieldMoney——多币种金额输入
 * FieldQuantity——带单位数量输入
 * @author AaronZZH & Kiro
 */

"use client"

import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

export interface MoneyValue {
  value: number
  currency: string
}

interface FieldMoneyProps {
  name: string
  label?: string
  value?: MoneyValue
  onChange?: (v: MoneyValue) => void
  currencies?: string[]
  defaultCurrency?: string
  disabled?: boolean
  error?: string
}

export function FieldMoney({
  name,
  label,
  value,
  onChange,
  currencies = ["CNY", "USD", "EUR"],
  defaultCurrency = "CNY",
  disabled,
  error
}: FieldMoneyProps) {
  const current = value ?? { value: 0, currency: defaultCurrency }

  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={name}>{label}</Label>}
      <div className="flex gap-2">
        <Input
          id={name}
          type="number"
          className="flex-1"
          value={current.value}
          onChange={(e) => onChange?.({ ...current, value: e.target.valueAsNumber })}
          disabled={disabled}
          aria-invalid={!!error}
        />
        <Select
          value={current.currency}
          onValueChange={(currency) =>
            onChange?.({ ...current, currency: currency ?? current.currency })
          }
          disabled={disabled}
        >
          <SelectTrigger className="w-24">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {currencies.map((c) => (
                <SelectItem key={c} value={c}>
                  {c}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}

export interface QuantityValue {
  value: number
  unit: string
}

interface FieldQuantityProps {
  name: string
  label?: string
  value?: QuantityValue
  onChange?: (v: QuantityValue) => void
  units?: string[]
  defaultUnit?: string
  disabled?: boolean
  error?: string
}

export function FieldQuantity({
  name,
  label,
  value,
  onChange,
  units = ["kg", "g", "lb"],
  defaultUnit = "kg",
  disabled,
  error
}: FieldQuantityProps) {
  const current = value ?? { value: 0, unit: defaultUnit }

  return (
    <div className="flex flex-col gap-1.5">
      {label && <Label htmlFor={name}>{label}</Label>}
      <div className="flex gap-2">
        <Input
          id={name}
          type="number"
          className="flex-1"
          value={current.value}
          onChange={(e) => onChange?.({ ...current, value: e.target.valueAsNumber })}
          disabled={disabled}
          aria-invalid={!!error}
        />
        <Select
          value={current.unit}
          onValueChange={(unit) => onChange?.({ ...current, unit: unit ?? current.unit })}
          disabled={disabled}
        >
          <SelectTrigger className="w-20">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              {units.map((u) => (
                <SelectItem key={u} value={u}>
                  {u}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      {error && <p className="text-destructive text-xs">{error}</p>}
    </div>
  )
}
