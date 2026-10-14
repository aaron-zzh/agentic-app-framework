package com.xuejiai.aaf.module.tool.medical;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.tool.websearch.IqsHttpClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 阿里云 IQS 医疗问答（MedicalAnswer）客户端。
 *
 * <p>接口：{@code POST /medical/answer}，基于权威医疗数据搜索后由大模型汇总提炼出结论型解答，并附依据来源。 服务端超时 5 秒，客户端放宽到 8
 * 秒。认证与错误解析见 {@link IqsHttpClient}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalAnswerClient {

    private static final String ANSWER_PATH = "/medical/answer";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final IqsHttpClient http;

    /**
     * 依据来源。
     *
     * @param title 来源标题（文献、图书、指南类以书名号包裹）
     * @param url 来源链接，论文、指南、图书类为空串
     * @param content 来源摘要，仅医疗网页、药品说明书类返回，其余为空串
     * @param type 来源类型，取值可能随数据源扩展而新增，调用方需按未知值兜底
     */
    public record Source(String title, String url, String content, String type) {}

    /** 一条解答：正文中的 [n] 为引用来源角标。 */
    public record Answer(String content, List<Source> sources) {}

    /** 查询结果；未命中内容时 answers 为空。 */
    public record AnswerResult(String requestId, List<Answer> answers) {}

    /**
     * 医疗问答。
     *
     * @param query 问题（1~200 字符，由调用方校验）
     */
    public AnswerResult query(String query) {
        var root = http.post(ANSWER_PATH, Map.of("query", query), REQUEST_TIMEOUT);
        var answers = new ArrayList<Answer>();
        for (var message : root.path("messages")) {
            var sources = new ArrayList<Source>();
            for (var doc : message.path("metaData").path("raDocList")) {
                sources.add(
                        new Source(
                                doc.path("title").asString(""),
                                doc.path("rawUrl").asString(""),
                                doc.path("content").asString(""),
                                doc.path("type").asString("")));
            }
            answers.add(new Answer(message.path("content").asString(""), List.copyOf(sources)));
        }
        var requestId = root.path("requestId").asString("");
        log.info("[MedicalAnswer] 问答完成: requestId={}, answers={}", requestId, answers.size());
        return new AnswerResult(requestId, List.copyOf(answers));
    }
}
