/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.middleware;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.engine.knowledge.importer.ImporterFactory;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import reactor.core.publisher.Flux;

/**
 * 文件处理中间件——在消息传入 LLM 之前，将 URL 按文件类型转换为模型可理解的内容：
 *
 * <ul>
 *   <li>图片（jpg/png/gif/webp/svg）→ {@link ImageBlock}（直接传给视觉模型）
 *   <li>文档（pdf/docx/md/txt/html）→ 解析提取文本，追加到消息 content
 * </ul>
 *
 * <p>URL 来源：消息文本中内嵌的 http/https URL。
 * <p>文档解析：委托 {@link ImporterFactory}，复用知识库导入管道。
 * <p>无法解析的格式：在消息末尾追加提示文字，告知模型文件类型不支持。
 */
public class FileProcessingMiddleware implements MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(FileProcessingMiddleware.class);

    /** 图片后缀（直接转 ImageBlock，交给视觉模型）*/
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");

    /** 文档后缀（解析为文本）*/
    private static final Set<String> DOC_EXTS = Set.of("pdf", "docx", "doc", "md", "txt", "html", "htm");

    /** 消息文本中 URL 提取正则 */
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE);

    private final ImporterFactory importerFactory;
    private final HttpClient httpClient;

    public FileProcessingMiddleware(ImporterFactory importerFactory) {
        this.importerFactory = importerFactory;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext ctx,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {

        if (input.msgs() == null || input.msgs().isEmpty()) {
            return next.apply(input);
        }

        // 只处理最后一条 USER 消息（当前轮用户输入）
        var msgs = input.msgs();
        Msg lastUser = null;
        int lastUserIdx = -1;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i) != null && msgs.get(i).getRole() == MsgRole.USER) {
                lastUser = msgs.get(i);
                lastUserIdx = i;
                break;
            }
        }
        if (lastUser == null) {
            return next.apply(input);
        }

        String text = lastUser.getTextContent();
        if (text == null || text.isBlank()) {
            return next.apply(input);
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        List<String> urls = new ArrayList<>();
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        if (urls.isEmpty()) {
            return next.apply(input);
        }

        // 构建新的 content 块列表
        List<ContentBlock> newBlocks = new ArrayList<>(lastUser.getContent());
        StringBuilder appendText = new StringBuilder();

        for (String url : urls) {
            String ext = extractExt(url);
            if (IMAGE_EXTS.contains(ext)) {
                // 图片：直接加 ImageBlock（视觉模型原生支持）
                newBlocks.add(ImageBlock.builder()
                        .source(new URLSource(url))
                        .build());
                log.debug("[FileProcessing] 图片 URL 转 ImageBlock: {}", url);
            } else if (DOC_EXTS.contains(ext)) {
                // 文档：下载后解析为文本
                String docText = parseDocument(url, ext);
                if (docText != null) {
                    appendText.append("\n\n---\n📎 文件内容（").append(url).append("）：\n").append(docText);
                    log.info("[FileProcessing] 文档解析成功: {} ({} 字符)", url, docText.length());
                }
            } else if (!ext.isEmpty()) {
                appendText.append("\n\n⚠️ 文件 ").append(url).append(" 格式（.").append(ext)
                        .append("）暂不支持直接解析，请复制文本内容后粘贴到对话框。");
            }
        }

        if (appendText.isEmpty() && newBlocks.size() == lastUser.getContent().size()) {
            return next.apply(input);
        }

        // 把追加文本合并到最后一个 TextBlock，或新增一个
        if (!appendText.isEmpty()) {
            boolean merged = false;
            for (int i = newBlocks.size() - 1; i >= 0; i--) {
                if (newBlocks.get(i) instanceof TextBlock tb) {
                    newBlocks.set(i, TextBlock.builder().text(tb.getText() + appendText).build());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                newBlocks.add(TextBlock.builder().text(appendText.toString()).build());
            }
        }

        Msg enriched = lastUser.withContent(newBlocks);
        List<Msg> newMsgs = new ArrayList<>(msgs);
        newMsgs.set(lastUserIdx, enriched);
        return next.apply(new AgentInput(newMsgs));
    }

    private String parseDocument(String url, String ext) {
        try {
            var importer = importerFactory.getImporter("file." + ext);
            if (importer.isEmpty()) {
                log.debug("[FileProcessing] 无对应 importer: {}", ext);
                return null;
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("User-Agent", "AAF/1.0")
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                log.warn("[FileProcessing] 下载失败 url={} status={}", url, resp.statusCode());
                return null;
            }
            var result = importer.get().importDocument(resp.body(), "file." + ext);
            if (result == null || result.sections().isEmpty()) return null;
            // 截断防止超 context window（最多 8000 字）
            var sb = new StringBuilder(8192);
            for (var section : result.sections()) {
                if (sb.length() + section.content().length() > 8000) {
                    sb.append("\n…（文档过长，已截断）");
                    break;
                }
                sb.append(section.content()).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("[FileProcessing] 解析失败 url={}: {}", url, e.getMessage());
            return null;
        }
    }

    private static String extractExt(String url) {
        // 去掉查询参数，提取后缀
        String path = url.split("[?#]")[0];
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) return "";
        String ext = path.substring(dot + 1).toLowerCase();
        // 限制合理后缀长度
        return ext.length() <= 5 ? ext : "";
    }
}
