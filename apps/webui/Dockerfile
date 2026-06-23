# ── 依赖安装阶段 ─────────────────────────────────────────
FROM node:24.16.0-alpine AS deps
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@latest --activate

# 只复制依赖声明，利用 layer 缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/webui/package.json ./apps/webui/
COPY packages/tailwind-config/package.json ./packages/tailwind-config/
COPY packages/core/package.json ./packages/core/
COPY packages/hooks/package.json ./packages/hooks/

RUN pnpm install --frozen-lockfile --filter @aaf/webui...

# ── 构建阶段 ────────────────────────────────────────────
FROM node:24.16.0-alpine AS builder
WORKDIR /app

RUN corepack enable && corepack prepare pnpm@latest --activate

COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/apps/webui/node_modules ./apps/webui/node_modules

COPY . .

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production

# 编译时注入公网 API 地址（来自 CI build-args → GitHub Secret: PUBLIC_API_URL）
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL

# tldraw 画布授权（来自 CI build-args → GitHub Secret: TLDRAW_LICENSE_KEY）
ARG NEXT_PUBLIC_TLDRAW_LICENSE_KEY
ENV NEXT_PUBLIC_TLDRAW_LICENSE_KEY=$NEXT_PUBLIC_TLDRAW_LICENSE_KEY

# 静态资源 CDN/OSS（可选，来自 CI build-args → .env.prod）
ARG NEXT_PUBLIC_ASSETS_URL
ENV NEXT_PUBLIC_ASSETS_URL=$NEXT_PUBLIC_ASSETS_URL

# 阿里云 ESA AI 验证码（来自 CI build-args → .env.prod）
ARG NEXT_PUBLIC_CAPTCHA_ENABLED=false
ARG NEXT_PUBLIC_CAPTCHA_REGION=cn
ARG NEXT_PUBLIC_CAPTCHA_PREFIX
ARG NEXT_PUBLIC_CAPTCHA_SCENE_ID
ENV NEXT_PUBLIC_CAPTCHA_ENABLED=$NEXT_PUBLIC_CAPTCHA_ENABLED
ENV NEXT_PUBLIC_CAPTCHA_REGION=$NEXT_PUBLIC_CAPTCHA_REGION
ENV NEXT_PUBLIC_CAPTCHA_PREFIX=$NEXT_PUBLIC_CAPTCHA_PREFIX
ENV NEXT_PUBLIC_CAPTCHA_SCENE_ID=$NEXT_PUBLIC_CAPTCHA_SCENE_ID

RUN pnpm nx build webui

# ── 运行阶段 ────────────────────────────────────────────
FROM node:24.16.0-alpine AS runner
WORKDIR /app

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production

RUN addgroup -S aaf && adduser -S aaf -G aaf

COPY --from=builder --chown=aaf:aaf /app/apps/webui/.next/standalone ./
COPY --from=builder --chown=aaf:aaf /app/apps/webui/.next/static ./apps/webui/.next/static
COPY --from=builder --chown=aaf:aaf /app/apps/webui/public ./apps/webui/public

USER aaf

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "apps/webui/server.js"]
