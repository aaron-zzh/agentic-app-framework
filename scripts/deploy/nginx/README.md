# AAF Nginx 模板

本目录提供生产部署参考模板：

- `nginx.conf.template`：Nginx 全局配置，放到 `/etc/nginx/nginx.conf`
- `conf.d/aaf.conf.template`：AAF 站点反代配置，放到 `/etc/nginx/conf.d/aaf.conf`

推荐生产拓扑：

```text
https://aaf.example.com
  /          -> AAF WebUI
  /api/**    -> AAF API
  /ws/**     -> AAF WebSocket
  /agui/**   -> AG-UI / SSE
```

模板变量示例：

```text
AAF_SERVER_NAME=aaf.example.com
AAF_WEBUI_UPSTREAM=127.0.0.1:3000
AAF_API_UPSTREAM=127.0.0.1:8080
AAF_SSL_CERTIFICATE=/etc/nginx/cert/aaf.example.com.pem
AAF_SSL_CERTIFICATE_KEY=/etc/nginx/cert/aaf.example.com.key
AAF_CLIENT_MAX_BODY_SIZE=100m
AAF_MONITOR_ALLOW_CIDR=10.0.0.0/8
```

替换变量后，执行：

```bash
nginx -t
systemctl reload nginx
```
