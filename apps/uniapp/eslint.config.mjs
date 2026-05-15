import uni from '@uni-helper/eslint-config'

export default uni(
  {
    unocss: true,
    markdown: false,
    rules: {
      'no-console': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
      'eslint-comments/no-unlimited-disable': 'off',
    },
    ignores: [
      'src/uni_modules/**/*',
      '**/*.md',
    ],
  },
)
