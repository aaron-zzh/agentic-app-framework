package com.xuejiai.aaf.module.tool.medical;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.tool.websearch.IqsHttpClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 IQS 健康图谱（MedicalKnow）客户端。
 *
 * <p>接口：{@code POST /medical/know}，基于权威医疗数据返回实体及属性的结构化文本。服务端超时 5 秒，客户端放宽到 8 秒。 认证与错误解析见 {@link
 * IqsHttpClient}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalKnowClient {

    private static final String KNOW_PATH = "/medical/know";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final IqsHttpClient http;

    /** 单个实体的属性文本（以「实体：X」起始，后接若干「- 属性[名]：值」行）与来源链接。 */
    public record KnowItem(String content, String url) {}

    /** 查询结果；未识别到实体时 items 为空。 */
    public record KnowResult(String requestId, List<KnowItem> items) {}

    /**
     * 查询医疗实体属性。
     *
     * @param query 实体名（1~200 字符，由调用方校验）
     * @param type 可选：finding（病症）/ food（食品）/ medicine（药品）；为空由服务端自动识别
     */
    public KnowResult query(String query, String type) {
        var body = new LinkedHashMap<String, String>();
        body.put("query", query);
        if (type != null && !type.isBlank()) {
            body.put("type", type);
        }
        var root = http.post(KNOW_PATH, body, REQUEST_TIMEOUT);
        var items = new ArrayList<KnowItem>();
        for (var message : root.path("messages")) {
            items.add(
                    new KnowItem(
                            message.path("content").asString(""),
                            message.path("url").asString("")));
        }
        var requestId = root.path("requestId").asString("");
        log.info(
                "[MedicalKnow] 查询完成: type={}, requestId={}, results={}",
                type,
                requestId,
                items.size());
        return new KnowResult(requestId, List.copyOf(items));
    }
}
