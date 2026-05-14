/*
 * @Author: weisheng
 * @Date: 2026-04-20 14:08:09
 * @LastEditTime: 2026-04-21 19:48:59
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /wot-starter/docs/.vitepress/theme/index.ts
 * 记得注释
 */
import { createWotVitePressTheme } from '@wot-ui/vitepress-theme'

export default createWotVitePressTheme({
  analytics: {
    trackBaiduRoute: true,
  },
  demoIframe: {
    // assetBase: '/wxqrcode',
    enabled: false,
    excludePatterns: ['/guide/skills', '/guide/open-wot', '/guide/llms-txt', '/guide/unocss-preset', '/guide/wot-ui', '/guide/uni-helper', '/guide/bundle-optimizer', '/guide/changelog', '/guide/deployment', '/guide/i18n', '/guide/introduction'],
    routePatterns: ['/guide'],
  },
  banner: {
    urls: ['https://sponsor.wot-ui.cn/wot-starter-v2-banner.json', 'https://wot-sponsors.pages.dev/wot-starter-v2-banner.json'],
  },
  specialSponsor: {
    enabled: false,
    urls: [],
  },
  ads: {
    wwadsId: '372',
    // urls: ['https://sponsor.wot-ui.cn/ads.json', 'https://wot-sponsors.pages.dev/ads.json'],
  },
  team: {
    urls: ['https://sponsor.wot-ui.cn/team.json', 'https://wot-sponsors.pages.dev/team.json'],
  },
  friendly: {
    urls: ['https://sponsor.wot-ui.cn/friendly.json', 'https://wot-sponsors.pages.dev/friendly.json'],
  },
})
