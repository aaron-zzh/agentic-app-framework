#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: bash generate-tls.sh --dns-name mqtt.example.com --output-dir ./emqx-certs [--valid-days 825] [--force]

Generates a private CA and an EMQX server certificate.
Upload <output-dir>/broker/ca.pem, cert.pem, and key.pem to /root/aaf/emqx-certs/.
Keep <output-dir>/ca/ca-key.pem private. Configure ModBot to trust <output-dir>/ca/ca.pem.
EOF
}

DNS_NAME=""
OUTPUT_DIR=""
VALID_DAYS=825
FORCE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dns-name)
      DNS_NAME="${2:-}"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="${2:-}"
      shift 2
      ;;
    --valid-days)
      VALID_DAYS="${2:-}"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$DNS_NAME" || -z "$OUTPUT_DIR" ]]; then
  usage >&2
  exit 1
fi
if [[ ! "$DNS_NAME" =~ ^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,}$ ]]; then
  echo "--dns-name 必须是有效域名，例如 mqtt.aaronzzh.cn" >&2
  exit 1
fi
if [[ ! "$VALID_DAYS" =~ ^[0-9]+$ ]] || (( VALID_DAYS < 1 || VALID_DAYS > 3650 )); then
  echo "--valid-days 必须是 1 到 3650 的整数" >&2
  exit 1
fi
if ! command -v openssl >/dev/null 2>&1; then
  echo "未找到 openssl。请先安装 OpenSSL。" >&2
  exit 1
fi

umask 077
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
CA_DIR="$OUTPUT_DIR/ca"
BROKER_DIR="$OUTPUT_DIR/broker"
CA_KEY="$CA_DIR/ca-key.pem"
CA_CERT="$CA_DIR/ca.pem"
BROKER_CA="$BROKER_DIR/ca.pem"
BROKER_KEY="$BROKER_DIR/key.pem"
BROKER_CERT="$BROKER_DIR/cert.pem"
SERVER_CSR="$BROKER_DIR/server.csr"
SERVER_CONFIG="$BROKER_DIR/server-ext.cnf"

if [[ "$FORCE" != true ]]; then
  for target in "$CA_KEY" "$CA_CERT" "$BROKER_CA" "$BROKER_KEY" "$BROKER_CERT"; do
    if [[ -e "$target" ]]; then
      echo "证书文件已存在：$target。请使用新的输出目录，或确认覆盖后添加 --force。" >&2
      exit 1
    fi
  done
done

mkdir -p "$CA_DIR" "$BROKER_DIR"
trap 'rm -f "$SERVER_CSR" "$SERVER_CONFIG"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:4096 -out "$CA_KEY"
openssl req -x509 -new -sha256 -days "$VALID_DAYS" \
  -key "$CA_KEY" \
  -out "$CA_CERT" \
  -subj '/CN=AAF ModBot MQTT Root CA'

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$BROKER_KEY"
openssl req -new -sha256 \
  -key "$BROKER_KEY" \
  -out "$SERVER_CSR" \
  -subj "/CN=$DNS_NAME"

cat > "$SERVER_CONFIG" <<EOF
subjectAltName = DNS:$DNS_NAME
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
EOF

SERIAL="$(openssl rand -hex 16)"
openssl x509 -req -sha256 -days "$VALID_DAYS" \
  -in "$SERVER_CSR" \
  -CA "$CA_CERT" \
  -CAkey "$CA_KEY" \
  -set_serial "0x$SERIAL" \
  -out "$BROKER_CERT" \
  -extfile "$SERVER_CONFIG"
cp "$CA_CERT" "$BROKER_CA"
openssl verify -CAfile "$CA_CERT" "$BROKER_CERT"

echo "证书已生成：$OUTPUT_DIR"
echo "上传到 ECS：$BROKER_DIR 下的 ca.pem、cert.pem、key.pem → /root/aaf/emqx-certs/"
echo "保留在本地：$CA_KEY"
echo "导入 ModBot：$CA_CERT"
