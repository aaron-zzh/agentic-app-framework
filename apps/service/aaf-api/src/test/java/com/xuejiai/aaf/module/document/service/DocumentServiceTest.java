package com.xuejiai.aaf.module.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.document.api.DocumentDraftApi.DraftUpsertCommand;
import com.xuejiai.aaf.module.document.domain.Document;
import com.xuejiai.aaf.module.document.repository.DocLinkRepository;
import com.xuejiai.aaf.module.document.repository.DocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class DocumentServiceTest extends BaseMockitoUnitTest {

    @Mock private DocumentRepository documents;
    @Mock private DocLinkRepository links;
    @Mock private DocImportService imports;
    @Mock private OperatorContext operators;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, links, imports, operators);
    }

    @Test
    void upsertDraftUsesExplicitScopeAndNeverPublishes() {
        when(documents.save(org.mockito.ArgumentMatchers.any(Document.class)))
                .thenAnswer(
                        invocation -> {
                            var document = invocation.getArgument(0, Document.class);
                            document.setId(41L);
                            return document;
                        });

        var saved =
                service.upsertDraft(
                        new DraftUpsertCommand("测试文案", "# Markdown", "copywriting", 7L, 9L, 11L));

        var document = ArgumentCaptor.forClass(Document.class);
        org.mockito.Mockito.verify(documents).save(document.capture());
        assertThat(saved.id()).isEqualTo(41L);
        assertThat(document.getValue())
                .extracting(
                        Document::getOwnerId,
                        Document::getOrgId,
                        Document::getWorkspaceId,
                        Document::getPublish,
                        Document::getStatus)
                .containsExactly(7L, 9L, 11L, "draft", "active");
    }
}
