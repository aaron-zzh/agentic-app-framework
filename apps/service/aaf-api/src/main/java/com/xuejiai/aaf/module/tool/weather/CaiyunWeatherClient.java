package com.xuejiai.aaf.module.tool.weather;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 彩云天气 v2.6 API HTTP 客户端。
 *
 * <p>v2.6 与 v3 的关键差异：appKey 放在 URL 路径中（{@code /v2.6/{appKey}/{lon},{lat}/endpoint}），
 * 签名规则相同：HMAC-SHA256，StringToSign = method:path:queryStr:appKey:nonce:timestamp
 *
 * <p>配置（application.yml）：
 *
 * <pre>
 * aaf:
 *   weather:
 *     caiyun:
 *       app-key: your_app_key
 *       app-secret: your_app_secret
 * </pre>
 */
@Slf4j
@Component
public class CaiyunWeatherClient {

    private static final String BASE_URL = "https://api.caiyunapp.com";
    private static final String V3_BASE_URL = "https://singer.caiyunhub.com";

    @Value("${aaf.weather.caiyun.app-key:}")
    private String appKey;

    @Value("${aaf.weather.caiyun.app-secret:}")
    private String appSecret;

    /**
     * 综合天气查询（实况 + 逐小时 + 逐日 + 预警），一次返回完整数据。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @param dailysteps 天级预报天数（1-15）
     * @param hourlysteps 小时级预报小时数（1-360）
     * @return API 原始 JSON 字符串
     */
    public String weather(double longitude, double latitude, int dailysteps, int hourlysteps) {
        Map<String, String> query = new TreeMap<>();
        query.put("alert", "true");
        query.put("dailysteps", String.valueOf(Math.min(15, Math.max(1, dailysteps))));
        query.put("hourlysteps", String.valueOf(Math.min(360, Math.max(1, hourlysteps))));
        String path = "/v2.6/" + appKey + "/" + longitude + "," + latitude + "/weather";
        return get(path, query);
    }

    /**
     * 仅查询实时天气。
     *
     * @param longitude 经度
     * @param latitude 纬度
     * @return API 原始 JSON 字符串
     */
    public String realtime(double longitude, double latitude) {
        String path = "/v2.6/" + appKey + "/" + longitude + "," + latitude + "/realtime";
        return get(path, Map.of());
    }

    /**
     * 行政区划查询（经纬度→省市区），仅支持中国大陆。
     *
     * <p>使用 v3 签名鉴权（{@code x-cy-app-key} header + 签名），与 v2.6 综合接口共用 sign 方法，
     * 仅 host 不同。控制台开启「强制签名」后 token 模式会被拒绝（403）。
     *
     * @return 原始 JSON，含 admins 数组（省/市/区）
     */
    public String reverseAdmins(double longitude, double latitude) {
        Map<String, String> query = new TreeMap<>();
        query.put("longitude", String.valueOf(longitude));
        query.put("latitude", String.valueOf(latitude));
        return getV3Signed("/v3/cartography/reverse_admins", query);
    }

    private String get(String path, Map<String, String> query) {
        try {
            String nonce = UUID.randomUUID().toString();
            long timestamp = Instant.now().getEpochSecond();
            String signature = sign("GET", path, nonce, timestamp, query);
            String queryStr = toQueryString(query);

            String urlStr = BASE_URL + path + (queryStr.isEmpty() ? "" : "?" + queryStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("x-cy-nonce", nonce);
            conn.setRequestProperty("x-cy-timestamp", String.valueOf(timestamp));
            conn.setRequestProperty("x-cy-signature", signature);

            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining());
            }
        } catch (Exception e) {
            log.error("[CaiyunWeather] 请求失败: path={}, err={}", path, e.getMessage(), e);
            throw new RuntimeException("天气查询失败: " + e.getMessage(), e);
        }
    }

    /**
     * v3 接口签名版 GET。
     *
     * <p>与 v2.6 接口共用签名公式（{@code method:path:query:appKey:nonce:timestamp}），区别在于：
     * v3 host 为 {@code singer.caiyunhub.com}，且 appKey 通过 {@code x-cy-app-key} header 传递（v2.6 拼在 path 内）。
     */
    private String getV3Signed(String path, Map<String, String> query) {
        try {
            String nonce = UUID.randomUUID().toString();
            long timestamp = Instant.now().getEpochSecond();
            String signature = sign("GET", path, nonce, timestamp, query);
            String queryStr = toQueryString(query);

            String urlStr = V3_BASE_URL + path + (queryStr.isEmpty() ? "" : "?" + queryStr);
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("x-cy-app-key", appKey);
            conn.setRequestProperty("x-cy-nonce", nonce);
            conn.setRequestProperty("x-cy-timestamp", String.valueOf(timestamp));
            conn.setRequestProperty("x-cy-signature", signature);

            try (BufferedReader reader =
                    new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining());
            }
        } catch (Exception e) {
            log.error("[CaiyunWeather] v3 请求失败: path={}, err={}", path, e.getMessage(), e);
            throw new RuntimeException("彩云 v3 请求失败: " + e.getMessage(), e);
        }
    }

    private String sign(
            String method, String path, String nonce, long timestamp, Map<String, String> query)
            throws Exception {
        // v2.6 签名：method:path:queryStr:appKey:nonce:timestamp，path 含 appKey
        String queryStr = toQueryString(query);
        String stringToSign =
                String.join(":", method, path, queryStr, appKey, nonce, String.valueOf(timestamp));
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = hmac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    private String toQueryString(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        return new TreeMap<>(query)
                .entrySet().stream()
                        .map(
                                e ->
                                        URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(
                                                        e.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
    }
}
