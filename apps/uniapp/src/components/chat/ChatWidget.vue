<script setup lang="ts">
/**
 * 对话内嵌交互组件（widget）
 * 后端 SSE 响应中携带 widget 字段，前端根据 type 渲染对应组件：
 * - form：基于 wd-form 渲染动态表单，提交后作为下一条消息发送
 * - select：单选/多选，选择后继续对话
 * - confirm：确认操作按钮
 * - card：展示卡片（商品、课程、用户信息等）
 */
import type { ChatMessage } from './ChatBubble.vue'

/** form widget 字段定义 */
interface FormField {
  name: string
  label: string
  type: 'text' | 'number' | 'textarea' | 'select'
  required?: boolean
  options?: Array<{ label: string, value: string | number }>
  placeholder?: string
}

/** select widget 选项 */
interface SelectOption {
  label: string
  value: string | number
}

/** card widget 数据项 */
interface CardItem {
  label: string
  value: string
}

/** form schema */
interface FormSchema {
  fields: FormField[]
  submitText?: string
}

/** select schema */
interface SelectSchema {
  options: SelectOption[]
  multiple?: boolean
  placeholder?: string
}

/** confirm schema */
interface ConfirmSchema {
  confirmText?: string
  cancelText?: string
  description?: string
}

/** card schema */
interface CardSchema {
  title?: string
  image?: string
  items: CardItem[]
  actionText?: string
  actionUrl?: string
}

type WidgetSchema = FormSchema | SelectSchema | ConfirmSchema | CardSchema

const props = defineProps<{
  widget: NonNullable<ChatMessage['widget']>
}>()

const emit = defineEmits<{
  /** 用户操作完成，将结果作为消息发送 */
  submit: [text: string]
}>()

// form 表单数据
const formData = ref<Record<string, string | number>>({})
// select 已选项
const selectedValues = ref<Array<string | number>>([])

const formSchema = computed(() => props.widget.schema as FormSchema)
const selectSchema = computed(() => props.widget.schema as SelectSchema)
const confirmSchema = computed(() => props.widget.schema as ConfirmSchema)
const cardSchema = computed(() => props.widget.schema as CardSchema)

/** 提交表单 */
function onFormSubmit() {
  const parts = formSchema.value.fields
    .filter(f => formData.value[f.name] !== undefined && formData.value[f.name] !== '')
    .map(f => `${f.label}：${formData.value[f.name]}`)
  emit('submit', parts.join('，'))
}

/** 选择选项 */
function onSelect(value: string | number) {
  if (selectSchema.value.multiple) {
    const idx = selectedValues.value.indexOf(value)
    if (idx >= 0)
      selectedValues.value.splice(idx, 1)
    else
      selectedValues.value.push(value)
  }
  else {
    selectedValues.value = [value]
    // 单选直接提交
    const option = selectSchema.value.options.find(o => o.value === value)
    emit('submit', option?.label ?? String(value))
  }
}

/** 多选确认提交 */
function onSelectConfirm() {
  const labels = selectSchema.value.options
    .filter(o => selectedValues.value.includes(o.value))
    .map(o => o.label)
  emit('submit', labels.join('、'))
}

/** 确认操作 */
function onConfirm() {
  emit('submit', confirmSchema.value.confirmText ?? '确认')
}

/** 取消操作 */
function onCancel() {
  emit('submit', confirmSchema.value.cancelText ?? '取消')
}

/** card 操作按钮 */
function onCardAction() {
  const schema = cardSchema.value
  if (schema.actionUrl)
    emit('submit', schema.actionUrl)
  else if (schema.actionText)
    emit('submit', schema.actionText)
}
</script>

<template>
  <view class="mt-2 rounded-2 border border-gray-100 bg-gray-50 p-3">
    <!-- form：动态表单 -->
    <template v-if="widget.type === 'form'">
      <wd-form>
        <view v-for="field in formSchema.fields" :key="field.name" class="mb-2">
          <wd-input
            v-if="field.type === 'text' || field.type === 'number'"
            v-model="formData[field.name] as string"
            :label="field.label"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            :type="field.type"
            label-width="80px"
          />
          <wd-textarea
            v-else-if="field.type === 'textarea'"
            v-model="formData[field.name] as string"
            :label="field.label"
            :placeholder="field.placeholder ?? `请输入${field.label}`"
            label-width="80px"
          />
          <wd-select-picker
            v-else-if="field.type === 'select'"
            v-model="formData[field.name]"
            :label="field.label"
            :columns="field.options?.map(o => ({ value: o.value, label: o.label })) ?? []"
            label-width="80px"
          />
        </view>
        <wd-button block size="small" @click="onFormSubmit">
          {{ formSchema.submitText ?? '提交' }}
        </wd-button>
      </wd-form>
    </template>

    <!-- select：单选/多选 -->
    <template v-else-if="widget.type === 'select'">
      <view class="flex flex-wrap gap-2">
        <wd-tag
          v-for="option in selectSchema.options"
          :key="option.value"
          :type="selectedValues.includes(option.value) ? 'primary' : 'default'"
          @click="onSelect(option.value)"
        >
          {{ option.label }}
        </wd-tag>
      </view>
      <view v-if="selectSchema.multiple && selectedValues.length" class="mt-2">
        <wd-button block size="small" @click="onSelectConfirm">
          确认选择（{{ selectedValues.length }}项）
        </wd-button>
      </view>
    </template>

    <!-- confirm：确认操作 -->
    <template v-else-if="widget.type === 'confirm'">
      <text v-if="confirmSchema.description" class="mb-2 block text-sm text-gray-600">
        {{ confirmSchema.description }}
      </text>
      <view class="flex gap-2">
        <wd-button size="small" plain @click="onCancel">
          {{ confirmSchema.cancelText ?? '取消' }}
        </wd-button>
        <wd-button size="small" type="primary" @click="onConfirm">
          {{ confirmSchema.confirmText ?? '确认' }}
        </wd-button>
      </view>
    </template>

    <!-- card：展示卡片 -->
    <template v-else-if="widget.type === 'card'">
      <image
        v-if="cardSchema.image"
        :src="cardSchema.image"
        class="mb-2 h-32 w-full rounded-1"
        mode="aspectFill"
      />
      <text v-if="cardSchema.title" class="mb-1 block text-sm font-medium text-gray-800">
        {{ cardSchema.title }}
      </text>
      <view v-for="item in cardSchema.items" :key="item.label" class="flex justify-between py-0.5">
        <text class="text-xs text-gray-500">{{ item.label }}</text>
        <text class="text-xs text-gray-800">{{ item.value }}</text>
      </view>
      <wd-button
        v-if="cardSchema.actionText"
        block
        size="small"
        class="mt-2"
        @click="onCardAction"
      >
        {{ cardSchema.actionText }}
      </wd-button>
    </template>
  </view>
</template>
