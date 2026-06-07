ARG ACR_REGISTRY
FROM ${ACR_REGISTRY}/pgvector:0.8.2-pg18

# 安装中文 locale
RUN apt-get update && \
    apt-get install -y --no-install-recommends locales && \
    echo "zh_CN.UTF-8 UTF-8" >> /etc/locale.gen && \
    locale-gen zh_CN.UTF-8 && \
    rm -rf /var/lib/apt/lists/*
