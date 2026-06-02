package com.xuejiai.aaf.module.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.document.service.DocumentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 内容发布工具——供 AI Agent 调用，将文档发布到各平台。
 *
 * <p>当前为桩实现（返回发布预览），后续对接各平台 API：
 *
 * <ul>
 *   <li>wechat — 微信公众号（图文消息接口）
 *   <li>xiaohongshu — 小红书（图文/视频笔记）
 *   <li>douyin — 抖音（图文/视频）
 *   <li>channels — 微信视频号
 * </ul>
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentPublishTool {

    private final DocumentService documentService;

    @Tool(
            description =
                    "将指定文档发布到目标平台。支持平台：wechat(公众号)、xiaohongshu(小红书)、"
                            + "douyin(抖音)、channels(视频号)。返回发布状态和预览链接。")
    public String publish(
            @ToolParam(description = "文档 ID") Long docId,
            @ToolParam(description = "目标平台：wechat/xiaohongshu/douyin/channels") String platform,
            @ToolParam(description = "发布格式：article(图文)/image_text(贴图)/video(视频)") String format) {
        var doc = documentService.getById(docId);
        log.info("发布文档 [{}] 到平台 [{}]，格式 [{}]", doc.getTitle(), platform, format);

        // TODO: 对接各平台 API，当前返回模拟结果
        return ("{\"status\":\"pending_review\",\"docId\":%d,\"title\":\"%s\","
                        + "\"platform\":\"%s\",\"format\":\"%s\","
                        + "\"message\":\"内容已提交到%s平台审核队列，预计 1-5 分钟完成发布\"}")
                .formatted(doc.getId(), doc.getTitle(), platform, format, platformName(platform));
    }

    @Tool(description = "查询文档在各平台的发布状态。")
    public String publishStatus(@ToolParam(description = "文档 ID") Long docId) {
        var doc = documentService.getById(docId);
        // TODO: 查询实际发布状态
        return "{\"docId\":%d,\"title\":\"%s\",\"platforms\":[]}"
                .formatted(doc.getId(), doc.getTitle());
    }

    private String platformName(String platform) {
        return switch (platform) {
            case "wechat" -> "微信公众号";
            case "xiaohongshu" -> "小红书";
            case "douyin" -> "抖音";
            case "channels" -> "视频号";
            default -> platform;
        };
    }
}
