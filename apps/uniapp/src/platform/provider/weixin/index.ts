/**
 * 微信平台 Provider
 * 覆盖：微信小程序 + H5 微信内置浏览器
 * JS-SDK 初始化（H5）、小程序更新检查
 */

/** 加载微信平台（H5 端初始化 JS-SDK，小程序端检查更新） */
function load(): void {
  // #ifdef MP-WEIXIN
  checkUpdate()
  // #endif

  // #ifdef H5
  // JS-SDK 签名由后端接口提供，在需要使用微信能力的页面按需调用
  // #endif
}

/** 检查小程序版本更新 */
function checkUpdate(silence = true): void {
  // #ifdef MP-WEIXIN
  if (!uni.canIUse('getUpdateManager'))
    return

  const updateManager = uni.getUpdateManager()
  updateManager.onCheckForUpdate((res) => {
    if (!res.hasUpdate) {
      if (!silence) {
        uni.showModal({ title: '当前已是最新版本', showCancel: false })
      }
      return
    }

    updateManager.onUpdateReady(() => {
      uni.showModal({
        title: '更新提示',
        content: '新版本已准备好，是否立即重启？',
        success: ({ confirm }) => {
          if (confirm)
            updateManager.applyUpdate()
        },
      })
    })
  })
  // #endif
}

/**
 * 微信小程序静默登录，获取 code
 * 返回 code 供后端换取 openid/session_key
 */
function login(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: ({ code }) => resolve(code),
      fail: err => reject(err),
    })
    // #endif

    // #ifndef MP-WEIXIN
    reject(new Error('wx.login 仅支持微信小程序'))
    // #endif
  })
}

const weixinProvider = {
  load,
  checkUpdate,
  login,
}

export default weixinProvider
