package com.xuejiai.aaf.module.knowledge.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.xuejiai.aaf.common.enums.knowledge.DocumentStatusEnum;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.framework.storage.FileService;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi.SourceDocumentCommand;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeDocument;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.system.file.service.FileUploadService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库文档上传服务，负责文件持久化、文档建档及事务提交后入队。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentUploadService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentQueueService queueService;
    private final FileService fileService;
    private final FileUploadService fileUploadService;
    private final DocumentSourceApi documentSourceApi;

    @Transactional
    public List<KnowledgeDocument> upload(KnowledgeBase knowledgeBase, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw exception(GlobalErrorCode.BAD_REQUEST);
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("知识库文档上传必须在事务中执行");
        }

        var uploadedKeys = new ArrayList<String>();
        var documents = new ArrayList<KnowledgeDocument>();
        registerTransactionCallbacks(uploadedKeys, documents);

        for (var file : files) {
            var originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.isBlank()) {
                throw exception(GlobalErrorCode.BAD_REQUEST);
            }
            var sourceFile = fileUploadService.uploadCurrent(file);
            uploadedKeys.add(sourceFile.key());
            var sourceDocument =
                    documentSourceApi.register(
                            new SourceDocumentCommand(
                                    sourceFile.fileId(),
                                    originalFilename,
                                    "knowledge_source",
                                    sourceFile.key(),
                                    null,
                                    sourceFile.uploaderId()));

            var document = new KnowledgeDocument();
            document.setKnowledgeBaseId(knowledgeBase.getId());
            document.setSourceDocumentId(sourceDocument.id());
            document.setUploadedBy(sourceFile.uploaderId());
            document.setSourceType("FILE");
            document.setSourceKey(sourceFile.key());
            document.setSourceUri(sourceFile.url());
            document.setTitle(originalFilename);
            document.setFilePath(sourceFile.key());
            document.setFileType(extractFileType(originalFilename));
            document.setFileSize(file.getSize());
            document.setStatus(DocumentStatusEnum.PENDING.getCode());
            document.setChunkCount(0);
            document.setOrgId(knowledgeBase.getOrgId());
            document.setWorkspaceId(knowledgeBase.getWorkspaceId());
            document.setOwnerId(knowledgeBase.getOwnerId());
            documents.add(documentRepository.save(document));
        }
        return List.copyOf(documents);
    }

    private void registerTransactionCallbacks(
            List<String> uploadedKeys, List<KnowledgeDocument> documents) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        RuntimeException firstFailure = null;
                        for (var document : documents) {
                            try {
                                queueService.enqueue(document);
                            } catch (RuntimeException failure) {
                                log.error("知识库文档任务入队失败，documentId={}", document.getId(), failure);
                                if (firstFailure == null) {
                                    firstFailure = failure;
                                }
                            }
                        }
                        if (firstFailure != null) {
                            throw firstFailure;
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_ROLLED_BACK) {
                            return;
                        }
                        for (var key : uploadedKeys) {
                            try {
                                fileService.delete(key);
                            } catch (RuntimeException failure) {
                                log.warn("回滚后清理知识库文件失败，key={}", key, failure);
                            }
                        }
                    }
                });
    }

    private String extractFileType(String filename) {
        var dot = filename.lastIndexOf('.');
        return dot < 0 ? "unknown" : filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }
}
