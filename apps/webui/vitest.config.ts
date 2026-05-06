import { defineConfig } from 'vitest/config';

// developer 单测配置：覆盖 src 下的 *.test.ts(x) / *.spec.ts(x)
// 显式排除 *.accept.test.ts(x)（由 tester 在 acceptance target 中执行）
export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    exclude: ['src/**/*.accept.{test,spec}.{ts,tsx}', 'node_modules', '.next'],
    passWithNoTests: true,
  },
});
