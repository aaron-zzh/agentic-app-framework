import { defineConfig } from 'vitest/config';

// tester 验收配置：只跑 *.accept.test.ts(x)，与 developer 单测隔离
// 后续若引入 Playwright，可改为 playwright.config 或并存
export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.accept.{test,spec}.{ts,tsx}'],
    passWithNoTests: true,
  },
});
