#!/bin/bash
# Vercel Ignored Build Step 脚本
# 只有 docs/ 或 apps/docs/ 目录有变更时才触发构建
# 返回 0 → 跳过构建；返回 1 → 执行构建
# 参考：https://vercel.com/docs/projects/overview#ignored-build-step

git diff HEAD^ HEAD --name-only | grep -qE '^(docs/|apps/docs/)' && exit 1 || exit 0
