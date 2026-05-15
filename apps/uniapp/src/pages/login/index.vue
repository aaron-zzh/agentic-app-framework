<script setup lang="ts">
definePage({
  name: 'login',
  style: { navigationBarTitleText: '', navigationStyle: 'custom' },
})

const router = useRouter()
const { loading, close: closeLoading } = useGlobalLoading()
const toast = useGlobalToast()

const agreed = ref(false)
const loginType = ref<'wx' | 'account'>('wx')

// H5 账号密码登录表单
const form = reactive({ mobile: '', password: '' })

/** 微信小程序一键登录（获取手机号） */
// eslint-disable-next-line ts/no-explicit-any
async function onWxLogin(e: any) {
  if (e.detail?.errMsg?.includes('fail')) {
    toast.error({ msg: '已取消授权' })
    return
  }
  if (!agreed.value) {
    toast.warning({ msg: '请先阅读并同意用户协议' })
    return
  }
  loading('登录中...')
  try {
    // 获取微信 code
    const { code } = await new Promise<{ code: string }>((resolve, reject) => {
      uni.login({ provider: 'weixin', success: res => resolve(res), fail: reject })
    })
    // TODO: 调用后端接口换取 token
    // const result = await authApi.wxLogin({ code, phoneCode: e.detail.code })
    // userStore.login(result.token, result.userInfo)
    console.warn('wx login code:', code, 'phoneCode:', e.detail.code)
    toast.success({ msg: '登录成功（待接入后端）' })
    router.replaceAll({ name: 'index' })
  }
  catch {
    toast.error({ msg: '登录失败，请重试' })
  }
  finally {
    closeLoading()
  }
}

/** H5 账号密码登录 */
async function onAccountLogin() {
  if (!agreed.value) {
    toast.warning({ msg: '请先阅读并同意用户协议' })
    return
  }
  if (!form.mobile || !/^1\d{10}$/.test(form.mobile)) {
    toast.error({ msg: '请输入正确的手机号' })
    return
  }
  if (!form.password) {
    toast.error({ msg: '请输入密码' })
    return
  }
  loading('登录中...')
  try {
    // TODO: 调用后端接口
    // const result = await authApi.login(form)
    // userStore.login(result.token, result.userInfo)
    toast.success({ msg: '登录成功（待接入后端）' })
    router.replaceAll({ name: 'index' })
  }
  catch {
    toast.error({ msg: '账号或密码错误' })
  }
  finally {
    closeLoading()
  }
}

function openAgreement() {
  uni.showToast({ title: '用户协议（待完善）', icon: 'none' })
}

function openPrivacy() {
  uni.showToast({ title: '隐私政策（待完善）', icon: 'none' })
}
</script>

<template>
  <view class="min-h-screen" style="background: linear-gradient(180deg, #8e44ad 0%, #3498db 40%, #f5f5f5 40%)">
    <!-- 顶部 Logo -->
    <view class="flex flex-col items-center pb-8 pt-16">
      <view class="h-20 w-20 flex items-center justify-center rounded-4 bg-white/20">
        <wd-icon name="home" size="40px" color="#fff" />
      </view>
      <text class="mt-3 text-xl text-white font-bold">校园互帮</text>
      <text class="mt-1 text-sm text-white/70">帮你解决生活琐事</text>
    </view>

    <!-- 登录卡片 -->
    <view class="mx-6 rounded-4 bg-white p-6 shadow-lg">
      <text class="block text-lg text-gray-800 font-bold">您好</text>
      <text class="mt-1 block text-sm text-gray-500">
        欢迎登录校园互帮
      </text>

      <!-- 微信小程序登录 -->
      <!-- #ifdef MP-WEIXIN -->
      <view class="mt-6">
        <button
          class="w-full rounded-2 py-3 text-white font-medium"
          style="background: #8e44ad"
          open-type="getPhoneNumber"
          @getphonenumber="onWxLogin"
          @tap="!agreed && toast.warning({ msg: '请先阅读并同意用户协议' })"
        >
          手机号快捷登录
        </button>
        <view class="mt-3 text-center">
          <text class="text-sm text-gray-400" @tap="loginType = 'account'">账号密码登录</text>
        </view>
      </view>
      <!-- #endif -->

      <!-- H5 / 账号密码登录 -->
      <!-- #ifndef MP-WEIXIN -->
      <view class="mt-6 flex flex-col gap-3">
        <wd-input
          v-model="form.mobile"
          placeholder="请输入手机号"
          type="number"
          maxlength="11"
          prefix-icon="phone"
          clearable
        />
        <wd-input
          v-model="form.password"
          placeholder="请输入密码"
          show-password
          prefix-icon="lock-on"
          clearable
        />
        <wd-button block @click="onAccountLogin">
          登录
        </wd-button>
      </view>
      <!-- #endif -->

      <!-- 切换登录方式（小程序内） -->
      <!-- #ifdef MP-WEIXIN -->
      <view v-if="loginType === 'account'" class="mt-4 flex flex-col gap-3">
        <wd-input v-model="form.mobile" placeholder="请输入手机号" type="number" maxlength="11" clearable />
        <wd-input v-model="form.password" placeholder="请输入密码" show-password clearable />
        <wd-button block @click="onAccountLogin">
          登录
        </wd-button>
      </view>
      <!-- #endif -->

      <!-- 用户协议 -->
      <view class="mt-4 flex items-start gap-2">
        <wd-checkbox v-model="agreed" />
        <text class="text-xs text-gray-500 leading-relaxed">
          我已阅读并同意
          <text class="text-[#8e44ad]" @tap="openAgreement">《用户协议》</text>
          和
          <text class="text-[#8e44ad]" @tap="openPrivacy">《隐私政策》</text>
        </text>
      </view>
    </view>
  </view>
</template>
