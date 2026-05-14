import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

/** 合并 Tailwind 类名，解决冲突覆盖 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs))
}
