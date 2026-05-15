<script setup lang="ts">
definePage({
  name: 'profile-edit',
  style: { navigationBarTitleText: '编辑资料' },
})

const userStore = useUserStore()
const toast = useGlobalToast()
const { upload, uploading } = useUploader({ mode: 'server' })

const form = reactive({
  avatar: userStore.userInfo?.avatar ?? '',
  nickname: userStore.userInfo?.nickname ?? '',
  mobile: userStore.userInfo?.mobile ?? '',
})

/** 小程序选择头像（open-type="chooseAvatar"） */
// eslint-disable-next-line ts/no-explicit-any
async function onChooseAvatar(e: any) {
  const tempPath = e.detail.avatarUrl
  if (!tempPath)
    return
  try {
    const url = await upload({ path: tempPath, name: 'avatar.jpg', type: 'image' })
    form.avatar = url
  }
  catch {
    toast.error({ msg: '头像上传失败' })
  }
}

/** H5 / APP 选择头像 */
function onChangeAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const tempPath = res.tempFilePaths[0]
      try {
        const url = await upload({ path: tempPath, name: 'avatar.jpg', type: 'image' })
        form.avatar = url
      }
      catch {
        toast.error({ msg: '头像上传失败' })
      }
    },
  })
}

/** 保存 */
async function onSave() {
  if (!form.nickname.trim()) {
    toast.warning({ msg: '请输入昵称' })
    return
  }
  try {
    // TODO: 调用接口保存
    // await userApi.updateProfile(form)
    userStore.setUserInfo({ avatar: form.avatar, nickname: form.nickname, mobile: form.mobile })
    toast.success({ msg: '保存成功' })
    uni.navigateBack()
  }
  catch {
    toast.error({ msg: '保存失败' })
  }
}
</script>

<template>
  <scroll-view scroll-y class="h-full bg-gray-50">
    <!-- 头像 -->
    <view class="mx-4 mt-4 flex flex-col items-center rounded-3 bg-white py-6">
      <view class="relative">
        <image
          :src="form.avatar || '/static/logo.svg'"
          class="h-20 w-20 rounded-full"
          mode="aspectFill"
        />
        <view class="absolute bottom-0 right-0 h-6 w-6 flex items-center justify-center rounded-full bg-[#8e44ad]">
          <wd-icon name="camera" size="14px" color="#fff" />
        </view>
      </view>

      <!-- 小程序一键选头像 -->
      <!-- #ifdef MP-WEIXIN -->
      <button
        class="mt-2 border-none bg-transparent text-sm text-[#8e44ad]"
        open-type="chooseAvatar"
        @chooseavatar="onChooseAvatar"
      >
        {{ uploading ? '上传中...' : '更换头像' }}
      </button>
      <!-- #endif -->

      <!-- H5 / APP -->
      <!-- #ifndef MP-WEIXIN -->
      <text class="mt-2 text-sm text-[#8e44ad]" @tap="onChangeAvatar">
        {{ uploading ? '上传中...' : '更换头像' }}
      </text>
      <!-- #endif -->
    </view>

    <!-- 表单 -->
    <view class="mx-4 mt-4 rounded-3 bg-white">
      <wd-cell-group>
        <wd-cell title="昵称">
          <template #right-icon>
            <wd-input
              v-model="form.nickname"
              placeholder="请输入昵称"
              :no-border="true"
              align="right"
            />
          </template>
        </wd-cell>
        <wd-cell title="手机号">
          <template #right-icon>
            <wd-input
              v-model="form.mobile"
              placeholder="请输入手机号"
              type="number"
              :maxlength="11"
              :no-border="true"
              align="right"
            />
          </template>
        </wd-cell>
      </wd-cell-group>
    </view>

    <!-- 保存按钮 -->
    <view class="mx-4 mt-6">
      <wd-button block @click="onSave">
        保存
      </wd-button>
    </view>
  </scroll-view>
</template>
