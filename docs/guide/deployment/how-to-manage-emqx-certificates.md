---
level: Practice
layer: Product
purpose: 说明 EMQX 证书和设备命令签名文件的用途、保管边界与部署位置
status: draft
version: 1.0.0
date: 2026-10-31
author: AaronZZH
---

# 如何管理 EMQX 证书与签名文件

## 目标

安全生成、上传和保管 EMQX 的 TLS 证书与 AAF 设备命令签名密钥。

## 生成目录

运行 `scripts/deploy/emqx/generate-tls.ps1` 或 `scripts/deploy/emqx/generate-tls.sh` 后，输出目录包含：

```text
emqx-certs/
├─ ca/
│  ├─ ca.pem
│  └─ ca-key.pem
├─ broker/
│  ├─ ca.pem
│  ├─ cert.pem
│  └─ key.pem
├─ aaf-device-command-private.der
└─ aaf-device-command-public.der
```

## CA 文件

| 文件 | 用途 | 保存位置 | 禁止事项 |
|---|---|---|---|
| `ca/ca.pem` | 私有根 CA 公钥证书，用于验证 EMQX 服务端证书 | 导入 ModBot 信任库；可在受控位置备份 | 不可替换为服务端 `cert.pem` |
| `ca/ca-key.pem` | 私有根 CA 私钥，用于签发或轮换服务端证书 | 仅离线安全备份 | 禁止上传 ECS、提交 Git、分发给 ModBot |

根 CA 私钥泄露后，攻击者可以签发被 ModBot 信任的伪造 Broker 证书。发生泄露时应重新签发 CA、Broker 证书，并替换全部 ModBot 信任的 CA。

## Broker 文件

将以下三个文件上传到 ECS 的 `/root/aaf/emqx-certs/`。`docker-compose.yml` 会将该目录只读挂载到 EMQX 容器。

| 文件 | 用途 | ECS 位置 | 使用方 |
|---|---|---|---|
| `broker/ca.pem` | 供 EMQX 提供完整 TLS 信任链中的 CA 证书 | `/root/aaf/emqx-certs/ca.pem` | EMQX |
| `broker/cert.pem` | `mqtt.aaronzzh.cn` 的服务端公钥证书，含 DNS SAN | `/root/aaf/emqx-certs/cert.pem` | EMQX、ModBot 校验 |
| `broker/key.pem` | Broker 服务端私钥 | `/root/aaf/emqx-certs/key.pem` | EMQX |

限制 `key.pem` 权限，仅允许部署账户读取。不要将 Broker 私钥导入 ModBot。

## AAF 命令签名文件

| 文件 | 用途 | 配置位置 | 使用方 |
|---|---|---|---|
| `aaf-device-command-private.der` | Ed25519 PKCS#8 私钥，对 AAF 下行命令签名 | Base64 后写入 ECS `/root/aaf/.env` 的 `AAF_DEVICE_MQTT_SIGNING_PRIVATE_KEY` | AAF 服务 |
| `aaf-device-command-public.der` | Ed25519 X.509 公钥，验证 AAF 下行命令签名 | Base64 后写入 ECS `/root/aaf/.env` 的 `AAF_DEVICE_MQTT_SIGNING_PUBLIC_KEY`；AAF 简报下发给 ModBot | AAF、ModBot |

不要上传 `.der` 文件到 ECS。将其 Base64 值写入 `/root/aaf/.env` 后，把原始私钥留在本地安全备份。

## 临时环境变量文件

`.env.tmp` 保存本次生成的非空部署值：

```text
AAF_DEVICE_MQTT_PASSWORD
EMQX_SHARED_SECRET
AAF_DEVICE_MQTT_SIGNING_PRIVATE_KEY
AAF_DEVICE_MQTT_SIGNING_PUBLIC_KEY
```

将这些值安全合并至 ECS `/root/aaf/.env`，不要把 `.env.tmp` 上传到 GitHub、提交 Git 或通过普通聊天渠道发送。

## 部署前检查

- DNS `mqtt.aaronzzh.cn` 已解析到 ECS 公网 IP。
- ECS 安全组和系统防火墙允许可信来源访问 TCP `8883`。
- `/root/aaf/emqx-certs/` 中存在 `ca.pem`、`cert.pem`、`key.pem`。
- ECS `/root/aaf/.env` 已配置 MQTT 密码、EMQX 共享密钥和 Ed25519 Base64 密钥。
- ModBot 已安装 `ca/ca.pem` 并使用 `mqtt.aaronzzh.cn:8883` 连接。
