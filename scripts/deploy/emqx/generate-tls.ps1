[CmdletBinding()]
param(
  [Parameter(Mandatory)]
  [ValidateNotNullOrEmpty()]
  [string]$DnsName,

  [Parameter(Mandatory)]
  [ValidateNotNullOrEmpty()]
  [string]$OutputDir,

  [ValidateRange(1, 3650)]
  [int]$ValidDays = 825,

  [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$openssl = Get-Command openssl -ErrorAction SilentlyContinue
if ($null -eq $openssl) {
  throw "未找到 openssl。请先安装 OpenSSL 并将其加入 PATH。"
}

$dnsName = $DnsName.Trim()
if ($dnsName -notmatch "^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,}$") {
  throw "DnsName 必须是有效域名，例如 mqtt.aaronzzh.cn。"
}

$outputPath = [System.IO.Path]::GetFullPath($OutputDir)
$caDir = Join-Path $outputPath "ca"
$brokerDir = Join-Path $outputPath "broker"
$caKey = Join-Path $caDir "ca-key.pem"
$caCert = Join-Path $caDir "ca.pem"
$brokerCa = Join-Path $brokerDir "ca.pem"
$brokerKey = Join-Path $brokerDir "key.pem"
$brokerCert = Join-Path $brokerDir "cert.pem"
$serverCsr = Join-Path $brokerDir "server.csr"
$serverConfig = Join-Path $brokerDir "server-ext.cnf"
$targets = @($caKey, $caCert, $brokerCa, $brokerKey, $brokerCert)

if (-not $Force -and ($targets | Where-Object { Test-Path $_ })) {
  throw "证书文件已存在。请使用新的 OutputDir，或确认覆盖后添加 -Force。"
}

New-Item -ItemType Directory -Force -Path $caDir, $brokerDir | Out-Null

function Invoke-OpenSsl([string[]]$Arguments) {
  & $openssl.Source @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "OpenSSL 命令执行失败：openssl $($Arguments -join ' ')"
  }
}

try {
  Invoke-OpenSsl @("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:4096", "-out", $caKey)
  Invoke-OpenSsl @(
    "req", "-x509", "-new", "-sha256", "-days", "$ValidDays",
    "-key", $caKey,
    "-out", $caCert,
    "-subj", "/CN=AAF ModBot MQTT Root CA"
  )

  Invoke-OpenSsl @("genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", $brokerKey)
  Invoke-OpenSsl @(
    "req", "-new", "-sha256",
    "-key", $brokerKey,
    "-out", $serverCsr,
    "-subj", "/CN=$dnsName"
  )

  @"
subjectAltName = DNS:$dnsName
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
"@ | Set-Content -Path $serverConfig -Encoding ascii

  $serial = (& $openssl.Source rand -hex 16).Trim()
  if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($serial)) {
    throw "无法生成证书序列号。"
  }
  Invoke-OpenSsl @(
    "x509", "-req", "-sha256", "-days", "$ValidDays",
    "-in", $serverCsr,
    "-CA", $caCert,
    "-CAkey", $caKey,
    "-set_serial", "0x$serial",
    "-out", $brokerCert,
    "-extfile", $serverConfig
  )
  Copy-Item -Force $caCert $brokerCa
  Invoke-OpenSsl @("verify", "-CAfile", $caCert, $brokerCert)
} finally {
  Remove-Item -Force -ErrorAction SilentlyContinue $serverCsr, $serverConfig
}

Write-Host "证书已生成：$outputPath"
Write-Host "上传到 ECS：$brokerDir 下的 ca.pem、cert.pem、key.pem → /root/aaf/emqx-certs/"
Write-Host "保留在本地：$caKey"
Write-Host "导入 ModBot：$caCert"
