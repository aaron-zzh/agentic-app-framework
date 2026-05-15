/**
 * 平台抽象层
 * 统一封装微信小程序 / H5 / APP 的平台差异，调用方无需写 #ifdef
 */

// #ifdef H5
// eslint-disable-next-line import/no-duplicates
import weixinProvider from './provider/weixin/index'
// #endif

// #ifdef MP-WEIXIN
// eslint-disable-next-line import/no-duplicates, ts/no-redeclare
import weixinProvider from './provider/weixin/index'
// #endif

const device = uni.getSystemInfoSync()

/** 平台名称 */
let name = 'unknown'
/** 是否微信生态 */
let isWeixin = false

// #ifdef MP-WEIXIN
name = 'mp-weixin'
isWeixin = true
// #endif

// #ifdef H5
name = 'h5'
// 判断 H5 是否在微信内置浏览器中
if (typeof window !== 'undefined' && /MicroMessenger/i.test(navigator.userAgent)) {
  isWeixin = true
}
// #endif

// #ifdef APP-PLUS
name = 'app'
// #endif

/** 加载平台前置行为（JS-SDK 初始化等） */
function load(): void {
  if (isWeixin) {
    weixinProvider.load()
  }
}

/** 检查网络连接 */
async function checkNetwork(): Promise<boolean> {
  const { networkType } = await uni.getNetworkType()
  return networkType !== 'none'
}

/** 获取小程序胶囊按钮信息（非小程序平台返回默认值） */
function getCapsule(): UniApp.GetMenuButtonBoundingClientRectRes {
  // #ifdef MP
  const capsule = uni.getMenuButtonBoundingClientRect()
  if (capsule)
    return capsule
  // #endif

  return { bottom: 56, height: 32, left: 278, right: 365, top: 24, width: 87 }
}

/** 导航栏高度（状态栏 + 44px 标题栏） */
const navbar = (device.statusBarHeight ?? 0) + 44

/** 胶囊信息 */
const capsule = getCapsule()

/** 检查小程序更新 */
function checkUpdate(silence = true): void {
  if (isWeixin) {
    weixinProvider.checkUpdate(silence)
  }
}

const platform = {
  name,
  isWeixin,
  device,
  navbar,
  capsule,
  load,
  checkNetwork,
  checkUpdate,
}

export default platform
