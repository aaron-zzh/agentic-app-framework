/**
 * AlertDialog——确认弹窗组件（shadcn/ui 风格）
 * @author AaronZZH & Kiro
 */

"use client"

import * as React from "react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle
} from "@/components/ui/dialog"

const AlertDialog = Dialog
const AlertDialogContent = DialogContent

function AlertDialogHeader({ children, ...props }: React.ComponentProps<typeof DialogHeader>) {
  return <DialogHeader {...props}>{children}</DialogHeader>
}

function AlertDialogFooter({ children, ...props }: React.ComponentProps<typeof DialogFooter>) {
  return <DialogFooter {...props}>{children}</DialogFooter>
}

function AlertDialogTitle({ children, ...props }: React.ComponentProps<typeof DialogTitle>) {
  return <DialogTitle {...props}>{children}</DialogTitle>
}

function AlertDialogDescription({
  children,
  ...props
}: React.ComponentProps<typeof DialogDescription>) {
  return <DialogDescription {...props}>{children}</DialogDescription>
}

function AlertDialogCancel({
  children,
  ...props
}: React.ComponentProps<typeof Button>) {
  return (
    <Button variant="outline" {...props}>
      {children}
    </Button>
  )
}

function AlertDialogAction({
  children,
  ...props
}: React.ComponentProps<typeof Button>) {
  return (
    <Button variant="destructive" {...props}>
      {children}
    </Button>
  )
}

export {
  AlertDialog,
  AlertDialogContent,
  AlertDialogHeader,
  AlertDialogFooter,
  AlertDialogTitle,
  AlertDialogDescription,
  AlertDialogCancel,
  AlertDialogAction
}
