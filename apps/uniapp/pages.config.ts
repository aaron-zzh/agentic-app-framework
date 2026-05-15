import { defineUniPages } from '@uni-helper/vite-plugin-uni-pages'

export default defineUniPages({
  pages: [],
  globalStyle: {
    navigationBarBackgroundColor: '@navBgColor',
    navigationBarTextStyle: '@navTxtStyle',
    navigationBarTitleText: '校园互帮',
    backgroundColor: '@bgColor',
    backgroundTextStyle: '@bgTxtStyle',
    backgroundColorTop: '@bgColorTop',
    backgroundColorBottom: '@bgColorBottom',
    enablePullDownRefresh: false,
    onReachBottomDistance: 50,
    animationType: 'pop-in',
    animationDuration: 300,
  },
  tabBar: {
    custom: true,
    // #ifdef MP-ALIPAY
    customize: true,
    overlay: true,
    // #endif
    height: '0',
    color: '@tabColor',
    selectedColor: '@tabSelectedColor',
    backgroundColor: '@tabBgColor',
    borderStyle: '@tabBorderStyle',
    list: [
      { pagePath: 'pages/index/index' },
      { pagePath: 'pages/chat/index' },
      { pagePath: 'pages/profile/index' },
    ],
  },
})
