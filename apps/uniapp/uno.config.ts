/*
 * @Author: weisheng
 * @Date: 2025-11-25 19:57:54
 * @LastEditTime: 2026-04-13 18:44:19
 * @LastEditors: weisheng
 * @Description:
 * @FilePath: /wot-starter/uno.config.ts
 * 记得注释
 */
import { presetUni } from '@uni-helper/unocss-preset-uni'
import { presetWot } from '@wot-ui/unocss-preset'

import {
  defineConfig,
  presetIcons,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'

export default defineConfig({
  presets: [
    presetUni({
      attributify: false,
    }),
    presetWot({
      preflight: false,
    }),
    presetIcons({
      scale: 1.2,
      warn: true,
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
      },
      // HBuilderX 必须针对要使用的 Collections 做异步导入
      // collections: {
      //   carbon: () => import('@iconify-json/carbon/icons.json').then(i => i.default),
      // },
    }),
  ],
  transformers: [
    transformerDirectives(),
    transformerVariantGroup(),
  ],
})
