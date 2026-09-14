package com.xuejiai.aaf.module.document.domain;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 文档在特定时刻的不可变全文快照。 */
@Getter
@Entity
@Table(name = "doc_document_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentVersion extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "parent_version_id")
    private Long parentVersionId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 32)
    private String format;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_ref", length = 255)
    private String sourceRef;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    public static DocumentVersion create(
            Long documentId,
            int versionNo,
            Long parentVersionId,
            String title,
            String content,
            String format,
            String checksum,
            String sourceType,
            String sourceRef,
            String changeSummary) {
        var version = new DocumentVersion();
        version.documentId = documentId;
        version.versionNo = versionNo;
        version.parentVersionId = parentVersionId;
        version.title = title;
        version.content = content;
        version.format = format;
        version.checksum = checksum;
        version.sourceType = sourceType;
        version.sourceRef = sourceRef;
        version.changeSummary = changeSummary;
        return version;
    }
}
