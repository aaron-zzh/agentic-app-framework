package com.xuejiai.aaf.module.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.framework.storage.FileVO;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

class KnowledgeDocumentUploadServiceTest extends BaseMockitoUnitTest {

    @Mock private KnowledgeDocumentRepository documentRepository;
    @Mock private KnowledgeDocumentQueueService queueService;
    @Mock private FileService fileService;
    @InjectMocks private KnowledgeDocumentUploadService uploadService;

    @Test
    @DisplayName("Given 文件上传成功 When 事务提交 Then 文档保存后才进入异步队列")
    void should_enqueue_after_transaction_commit() {
        // 准备参数
        var knowledgeBase = knowledgeBase();
        var file =
                new MockMultipartFile("files", "guide.md", "text/markdown", "content".getBytes());
        when(fileService.upload(file))
                .thenReturn(
                        new FileVO(
                                "kb/guide.md",
                                "/files/kb/guide.md",
                                "guide.md",
                                7,
                                "text/markdown"));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(
                        invocation -> {
                            KnowledgeDocument document = invocation.getArgument(0);
                            document.setId(11L);
                            return document;
                        });
        TransactionSynchronizationManager.initSynchronization();

        try {
            // 调用
            var documents = uploadService.upload(knowledgeBase, new MockMultipartFile[] {file});

            // 断言
            assertThat(documents).hasSize(1);
            assertThat(documents.getFirst().getFilePath()).isEqualTo("kb/guide.md");
            assertThat(documents.getFirst().getOwnerId()).isEqualTo(7L);
            verify(queueService, never()).enqueue(any());

            TransactionSynchronizationUtils.triggerAfterCommit();
            verify(queueService).enqueue(documents.getFirst());
            TransactionSynchronizationUtils.triggerAfterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given 批量文档部分入队失败 When 事务提交 Then 继续派发其余任务并报告失败")
    void should_continue_enqueuing_after_one_document_fails() {
        // 准备参数
        var first = new MockMultipartFile("files", "first.md", "text/markdown", "first".getBytes());
        var second =
                new MockMultipartFile("files", "second.md", "text/markdown", "second".getBytes());
        when(fileService.upload(first))
                .thenReturn(
                        new FileVO(
                                "kb/first.md",
                                "/files/kb/first.md",
                                "first.md",
                                5,
                                "text/markdown"));
        when(fileService.upload(second))
                .thenReturn(
                        new FileVO(
                                "kb/second.md",
                                "/files/kb/second.md",
                                "second.md",
                                6,
                                "text/markdown"));
        var nextId = new java.util.concurrent.atomic.AtomicLong(10L);
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(
                        invocation -> {
                            KnowledgeDocument document = invocation.getArgument(0);
                            document.setId(nextId.incrementAndGet());
                            return document;
                        });
        TransactionSynchronizationManager.initSynchronization();

        try {
            // 调用
            var documents =
                    uploadService.upload(knowledgeBase(), new MockMultipartFile[] {first, second});
            when(queueService.enqueue(documents.getFirst()))
                    .thenThrow(new IllegalStateException("redis unavailable"));

            // 断言
            assertThatThrownBy(TransactionSynchronizationUtils::triggerAfterCommit)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("redis unavailable");
            verify(queueService).enqueue(documents.getFirst());
            verify(queueService).enqueue(documents.get(1));
            verify(fileService, never()).delete(any());
            TransactionSynchronizationUtils.triggerAfterCompletion(
                    TransactionSynchronization.STATUS_COMMITTED);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("Given 文件已持久化 When 数据库事务回滚 Then 删除已上传文件且不入队")
    void should_delete_file_when_transaction_rolls_back() {
        // 准备参数
        var file =
                new MockMultipartFile("files", "guide.md", "text/markdown", "content".getBytes());
        when(fileService.upload(file))
                .thenReturn(
                        new FileVO(
                                "kb/guide.md",
                                "/files/kb/guide.md",
                                "guide.md",
                                7,
                                "text/markdown"));
        when(documentRepository.save(any(KnowledgeDocument.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        try {
            // 调用
            uploadService.upload(knowledgeBase(), new MockMultipartFile[] {file});
            TransactionSynchronizationUtils.triggerAfterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK);

            // 断言
            verify(fileService).delete("kb/guide.md");
            verify(queueService, never()).enqueue(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private KnowledgeBase knowledgeBase() {
        var knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(3L);
        knowledgeBase.setOrgId(5L);
        knowledgeBase.setWorkspaceId(6L);
        knowledgeBase.setOwnerId(7L);
        return knowledgeBase;
    }
}
