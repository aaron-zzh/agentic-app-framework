package com.xuejiai.aaf.module.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftUpsertCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.CreateDocumentVersionCommand;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentVersionView;
import com.xuejiai.aaf.module.document.repository.DocLinkRepository;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DocumentServiceTest extends BaseMockitoUnitTest {

    @Mock private DocumentRepository documents;
    @Mock private DocLinkRepository links;
    @Mock private DocImportService imports;
    @Mock private DocumentVersionService versions;
    @Mock private DocumentVersionSemanticMetadataService semanticMetadata;
    @Mock private OperatorContext operators;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service =
                new DocumentService(
                        documents, links, imports, versions, semanticMetadata, operators);
    }

    @Test
    void upsertDraftUsesExplicitScopeAndNeverPublishes() {
        when(versions.create(any(CreateDocumentVersionCommand.class)))
                .thenReturn(
                        new DocumentVersionView(
                                51L,
                                41L,
                                1,
                                null,
                                "测试文案",
                                "# Markdown",
                                "MARKDOWN",
                                "checksum",
                                "AIGC",
                                "content.draft.upsert",
                                "文案智能体生成草稿",
                                7L,
                                "USER",
                                null));

        var saved =
                service.upsertDraft(
                        new DraftUpsertCommand(
                                null, null, "测试文案", "# Markdown", "copywriting", 7L, 9L, 11L));

        var command = ArgumentCaptor.forClass(CreateDocumentVersionCommand.class);
        org.mockito.Mockito.verify(versions).create(command.capture());
        assertThat(saved.documentId()).isEqualTo(41L);
        assertThat(saved.documentVersionId()).isEqualTo(51L);
        assertThat(command.getValue())
                .extracting(
                        CreateDocumentVersionCommand::title,
                        CreateDocumentVersionCommand::content,
                        CreateDocumentVersionCommand::format,
                        CreateDocumentVersionCommand::documentType,
                        CreateDocumentVersionCommand::sourceType,
                        CreateDocumentVersionCommand::ownerId,
                        CreateDocumentVersionCommand::orgId,
                        CreateDocumentVersionCommand::workspaceId)
                .containsExactly(
                        "测试文案", "# Markdown", "MARKDOWN", "copywriting", "AIGC", 7L, 9L, 11L);
    }
}
