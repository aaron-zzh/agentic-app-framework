<script setup lang="ts">
definePage({
  name: 'admin-settings',
  meta: { requiresAuth: true, requiresAdmin: true },
  style: { navigationBarTitleText: '系统配置' },
})

interface ConfigItem {
  key: string
  label: string
  value: string
  type: 'text' | 'switch' | 'number'
}

const configs = ref<ConfigItem[]>([])
const loading = ref(false)

onLoad(async () => {
  loading.value = true
  try {
    configs.value = await alovaInstance.Get<ConfigItem[]>('/admin/config/list')
  }
  finally {
    loading.value = false
  }
})

async function onSave() {
  const data = Object.fromEntries(configs.value.map(c => [c.key, c.value]))
  await alovaInstance.Post('/admin/config/batch-save', data)
  useGlobalToast().success({ msg: '保存成功' })
}
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <wd-loading v-if="loading" class="flex justify-center py-8" />

    <view v-else class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell
          v-for="item in configs"
          :key="item.key"
          :title="item.label"
        >
          <template #right-icon>
            <wd-switch
              v-if="item.type === 'switch'"
              :model-value="item.value === '1'"
              @change="(v: boolean) => item.value = v ? '1' : '0'"
            />
            <wd-input
              v-else
              v-model="item.value"
              :type="item.type === 'number' ? 'number' : 'text'"
              :no-border="true"
              align="right"
            />
          </template>
        </wd-cell>
      </wd-cell-group>
    </view>

    <view class="mx-4 mt-4">
      <wd-button block @click="onSave">
        保存配置
      </wd-button>
    </view>
  </scroll-view>
</template>
