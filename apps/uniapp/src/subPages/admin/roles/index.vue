<script setup lang="ts">
definePage({
  name: 'admin-roles',
  meta: { requiresAuth: true, requiresAdmin: true },
  style: { navigationBarTitleText: '角色权限' },
})

interface Role {
  id: number
  name: string
  code: string
  permissions: string[]
  remark?: string
}

const { list, loading, refresh } = usePage<Role>('/admin/role/page')

onPullDownRefresh(() => {
  refresh()
  uni.stopPullDownRefresh()
})

const editingRole = ref<Role | null>(null)
const showEdit = ref(false)

function onEdit(role: Role) {
  editingRole.value = { ...role }
  showEdit.value = true
}

async function onSaveRole() {
  if (!editingRole.value)
    return
  await alovaInstance.Put(`/admin/role/${editingRole.value.id}`, editingRole.value)
  showEdit.value = false
  refresh()
  useGlobalToast().success({ msg: '保存成功' })
}
</script>

<template>
  <view class="h-full bg-gray-50">
    <wd-loading v-if="loading && !list.length" class="flex justify-center py-8" />
    <wd-empty v-else-if="!list.length" description="暂无角色" />

    <view v-else class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell
          v-for="role in list"
          :key="role.id"
          :title="role.name"
          :label="role.code"
          is-link
          @click="onEdit(role)"
        />
      </wd-cell-group>
    </view>

    <!-- 编辑弹窗 -->
    <wd-popup v-model="showEdit" position="bottom" round>
      <view v-if="editingRole" class="p-4">
        <text class="mb-4 block text-base font-bold">编辑角色：{{ editingRole.name }}</text>
        <wd-input v-model="editingRole.remark" placeholder="备注" class="mb-3" />
        <wd-button block @click="onSaveRole">
          保存
        </wd-button>
      </view>
    </wd-popup>
  </view>
</template>
