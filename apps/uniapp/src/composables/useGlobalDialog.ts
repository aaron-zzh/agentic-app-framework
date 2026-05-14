import type { DialogOptions, DialogResult } from '@wot-ui/ui/components/wd-dialog/types'
import { defineStore } from 'pinia'

export type GlobalDialogOptions = DialogOptions & {
  success?: (res: DialogResult) => void
  fail?: (res: DialogResult) => void
}

interface GlobalDialog {
  dialogOptions: GlobalDialogOptions | null
  currentPage: string
}

export const useGlobalDialog = defineStore('global-Dialog', {
  state: (): GlobalDialog => ({
    dialogOptions: null,
    currentPage: '',
  }),
  actions: {
    show(option: GlobalDialogOptions | string) {
      this.currentPage = getCurrentPath()
      this.dialogOptions = {
        ...(CommonUtil.isString(option) ? { title: option } : option),
        cancelButtonProps: {
          round: false,
        },
        confirmButtonProps: {
          round: false,
        },
      }
    },
    alert(option: GlobalDialogOptions | string) {
      const DialogOptions = CommonUtil.deepMerge({ type: 'alert' }, CommonUtil.isString(option) ? { title: option } : option) as DialogOptions
      DialogOptions.showCancelButton = false
      this.show(DialogOptions)
    },
    confirm(option: GlobalDialogOptions | string) {
      const DialogOptions = CommonUtil.deepMerge({ type: 'confirm' }, CommonUtil.isString(option) ? { title: option } : option) as DialogOptions
      DialogOptions.showCancelButton = true
      this.show(DialogOptions)
    },
    prompt(option: GlobalDialogOptions | string) {
      const DialogOptions = CommonUtil.deepMerge({ type: 'prompt' }, CommonUtil.isString(option) ? { title: option } : option) as DialogOptions
      DialogOptions.showCancelButton = true
      this.show(DialogOptions)
    },
    close() {
      this.dialogOptions = null
      this.currentPage = ''
    },
  },
})
