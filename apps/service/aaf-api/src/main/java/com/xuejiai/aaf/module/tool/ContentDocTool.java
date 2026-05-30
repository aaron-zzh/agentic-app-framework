package com.xuejiai.aaf.module.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.document.service.DocumentService;
import com.xuejiai.aaf.module.document.vo.DocCreateDTO;

import lombok.RequiredArgsConstructor;

/**
 * 内容文档工具——供 AI Agent 调用，完成内容创建和更新。
 *
 * @author AaronZZH & Kiro
 */
@Component
@RequiredArgsConstructor
public class ContentDocTool {

    private final DocumentService documentService;

    @Tool(description = "创建内容文档并保存到文档库。返回文档 ID 和标题。")
    public String createDocument(
            @ToolParam(description = "文档标题") String title,
            @ToolParam(description = "文档正文内容（Markdown 格式）") String content,
            @ToolParam(description = "文档类型：article(文章)/note(笔记)/script(脚本)")
                    String docType) {
        var doc = documentService.create(new DocCreateDTO(title, null, content, docType));
        return "{\"id\":%d,\"title\":\"%s\",\"docType\":\"%s\"}"
                .formatted(doc.getId(), doc.getTitle(), doc.getDocType());
    }

    @Tool(description = "更新已有文档的内容。传入文档 ID 和新的 Markdown 内容。")
    public String updateDocument(
            @ToolParam(description = "文档 ID") Long docId,
            @ToolParam(description = "新的文档内容（Markdown 格式）") String content) {
        var doc = documentService.update(docId, content);
        return "{\"id\":%d,\"title\":\"%s\",\"updated\":true}".formatted(doc.getId(), doc.getTitle());
    }
}
