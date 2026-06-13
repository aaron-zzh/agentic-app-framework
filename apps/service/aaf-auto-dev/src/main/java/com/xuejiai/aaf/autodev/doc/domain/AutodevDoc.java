package com.xuejiai.aaf.autodev.doc.domain;

import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 开发文档实体（对应 autodev_doc 表，docs/ 目录同步专用）。 */
@Getter
@Setter
@Entity
@Table(name = "autodev_doc")
@SQLDelete(sql = "UPDATE autodev_doc SET deleted = TRUE WHERE id = ?")
@SQLRestriction("deleted = FALSE")
public class AutodevDoc extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "doc_type", length = 50)
    private String docType = "spec";

    @Column(length = 20)
    private String status = "active";

    /** Front Matter 元数据（YAML 解析后存为 JSON 字符串）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "front_matter", columnDefinition = "JSONB")
    private String frontMatterJson;

    @Transient private static final ObjectMapper MAPPER = new ObjectMapper();

    public Map<String, Object> getFrontMatter() {
        if (frontMatterJson == null || frontMatterJson.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(frontMatterJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    public void setFrontMatter(Map<String, Object> fm) {
        if (fm == null || fm.isEmpty()) {
            this.frontMatterJson = null;
            return;
        }
        try {
            this.frontMatterJson = MAPPER.writeValueAsString(fm);
        } catch (JsonProcessingException e) {
            this.frontMatterJson = null;
        }
    }
}
