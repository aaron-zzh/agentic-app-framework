package com.xuejiai.aaf.module.knowledge.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi;
import com.xuejiai.aaf.module.document.api.DocumentSourceApi.SourceDocumentCommand;
import com.xuejiai.aaf.module.knowledge.domain.KnowledgeBase;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动时将 {@code bootstrap/} 下的内置资料登记为平台向导知识库归属者的文档库文档。
 *
 * <p>只写文档表，不导入知识库：向量化与实体抽取消耗模型额度，由管理员在知识库页面"从文档库导入"时按需触发。 按来源键幂等：文档已存在时跳过且不更新正文。新增内置资料只需在 {@link
 * #RESOURCES} 追加一项。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class PlatformGuideDocumentInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final UUID PLATFORM_GUIDE_KNOWLEDGE_BASE_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final String DOCUMENT_TYPE = "platform-guide";

    /** 内置资料清单：资源路径与登记为文档时的标题。 */
    private static final List<BootstrapResource> RESOURCES =
            List.of(
                    new BootstrapResource("bootstrap/aaf-framework-introduction.md", "AAF 框架介绍"),
                    new BootstrapResource("bootstrap/aaf-products-and-services.md", "产品与服务介绍"));

    private final UserRepository userRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentSourceApi documentSourceApi;
    private final PermissionExecutionService permissionExecutionService;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(ADMIN_USERNAME).isEmpty()) {
            log.info("未找到内置 admin，跳过平台向导文档初始化");
            return;
        }
        var knowledgeBase =
                OrgContext.runIgnoring(
                                () ->
                                        knowledgeBaseRepository.findByStableId(
                                                PLATFORM_GUIDE_KNOWLEDGE_BASE_ID))
                        .orElse(null);
        if (knowledgeBase == null) {
            log.warn("平台向导知识库不存在，跳过文档初始化，stableId={}", PLATFORM_GUIDE_KNOWLEDGE_BASE_ID);
            return;
        }

        var registered =
                permissionExecutionService.runAsOwner(
                        knowledgeBase.getOwnerId(),
                        "platform-guide-document-initialization",
                        () ->
                                OrgContext.runIfAbsent(
                                        knowledgeBase.getOrgId(),
                                        knowledgeBase.getWorkspaceId(),
                                        () -> registerResources(knowledgeBase)));
        log.info(
                "平台向导文档初始化完成，registered={}, skipped={}", registered, RESOURCES.size() - registered);
    }

    private int registerResources(KnowledgeBase knowledgeBase) {
        var ownerId = knowledgeBase.getOwnerId();
        var registered = 0;
        for (var resource : RESOURCES) {
            if (documentSourceApi.findBySourceKey(resource.sourceKey(), ownerId).isPresent()) {
                continue;
            }
            register(resource, ownerId);
            registered++;
        }
        return registered;
    }

    private void register(BootstrapResource resource, Long ownerId) {
        var classpathResource = new ClassPathResource(resource.path());
        if (!classpathResource.exists()) {
            throw new IllegalStateException("平台向导内置资料不存在: " + resource.path());
        }
        try {
            var content = classpathResource.getContentAsString(StandardCharsets.UTF_8);
            var document =
                    documentSourceApi.register(
                            new SourceDocumentCommand(
                                    null,
                                    resource.title(),
                                    DOCUMENT_TYPE,
                                    resource.sourceKey(),
                                    content,
                                    ownerId));
            log.info("已登记平台向导内置资料，path={}, documentId={}", resource.path(), document.id());
        } catch (IOException failure) {
            throw new IllegalStateException("读取平台向导内置资料失败: " + resource.path(), failure);
        }
    }

    /** 内置资料：classpath 路径作为文档来源键，保证重复启动幂等。 */
    private record BootstrapResource(String path, String title) {

        String sourceKey() {
            return "classpath:" + path;
        }
    }
}
