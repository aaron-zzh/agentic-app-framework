package com.xuejiai.aaf.module.document.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档实体。
 *
 * <p>对应 doc_document 表，支持 Markdown 内容存储和 Front Matter 元数据。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@Setter
@Entity
@Table(name = "doc_document")
@SQLDelete(
        sql =
                "UPDATE doc_document SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class Document extends BaseEntity {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 相对于项目根目录的文件路径 */
    @Column(name = "file_path", length = 500)
    private String filePath;

    /** 文档全量 Markdown 内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Front Matter 元数据（JSONB 存储，Java 侧用 String 持久化） */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "front_matter", columnDefinition = "jsonb")
    private String frontMatterJson;

    /** spec/design/task/guide/reference/explanation */
    @Column(name = "doc_type", nullable = false, length = 50)
    private String docType = "guide";

    /** active/archived */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "active";

    /** 发布状态：draft / published */
    @Column(name = "publish", nullable = false, length = 20)
    private String publish = "draft";

    /** 来源文件 ID（sys_file.id），PDF 导入时非空 */
    @Column(name = "source_file_id")
    private Long sourceFileId;

    /** 获取 Front Matter 为 Map。 */
    @Transient
    public Map<String, Object> getFrontMatter() {
        if (frontMatterJson == null || frontMatterJson.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(frontMatterJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    /** 设置 Front Matter（Map → JSON 字符串）。 */
    public void setFrontMatter(Map<String, Object> frontMatter) {
        if (frontMatter == null || frontMatter.isEmpty()) {
            this.frontMatterJson = null;
            return;
        }
        try {
            this.frontMatterJson = MAPPER.writeValueAsString(frontMatter);
        } catch (JsonProcessingException e) {
            this.frontMatterJson = null;
        }
    }
}
