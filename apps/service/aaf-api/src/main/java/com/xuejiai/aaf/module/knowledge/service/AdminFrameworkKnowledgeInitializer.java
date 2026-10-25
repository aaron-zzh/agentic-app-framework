package com.xuejiai.aaf.module.knowledge.service;

import java.io.IOException;
import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantDefinitionPort;
import com.xuejiai.aaf.framework.intelligent.assistant.port.AssistantProvisioningPort;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.UserId;
import com.xuejiai.aaf.framework.org.OrgContext;
import com.xuejiai.aaf.framework.security.PermissionExecutionService;
import com.xuejiai.aaf.module.knowledge.api.PersonalKnowledgeApi;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeBaseRepository;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeDocumentRepository;
import com.xuejiai.aaf.module.system.org.service.OrganizationService;
import com.xuejiai.aaf.module.system.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 为内置 admin 的默认知识库幂等导入框架介绍资源。 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminFrameworkKnowledgeInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String RESOURCE_PATH = "bootstrap/aaf-framework-introduction.md";
    private static final String SOURCE_KEY = "classpath:" + RESOURCE_PATH;

    private final UserRepository userRepository;
    private final OrganizationService organizationService;
    private final AssistantProvisioningPort assistantProvisioningPort;
    private final AssistantDefinitionPort assistantDefinitionPort;
    private final PersonalKnowledgeApi personalKnowledgeApi;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeDocumentUploadService knowledgeDocumentUploadService;
    private final PermissionExecutionService permissionExecutionService;

    @Override
    public void run(ApplicationArguments args) {
        var admin = userRepository.findByUsername(ADMIN_USERNAME).orElse(null);
        if (admin == null) {
            log.info("未找到内置 admin，跳过框架介绍知识库初始化");
            return;
        }

        var userId = new UserId(admin.getId().toString());
        var personalOrgId = organizationService.findPersonalOrgId(admin.getId()).orElse(null);
        assistantProvisioningPort.provisionDefaultForUser(userId);
        assistantProvisioningPort.ensureDefaultKnowledgeBase(
                userId,
                () ->
                        personalKnowledgeApi.createDefaultKnowledgeBase(
                                admin.getId(), personalOrgId));

        var tenantId =
                new TenantId(
                        String.valueOf(Objects.requireNonNullElse(personalOrgId, admin.getId())));
        var assistant =
                assistantDefinitionPort
                        .findDefaultForUser(tenantId, userId)
                        .orElseThrow(() -> new IllegalStateException("admin 默认 Assistant 不存在"));
        var knowledgeBaseStableId =
                assistant.knowledgeBaseIds().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("admin 默认 Assistant 未绑定知识库"));
        var knowledgeBase =
                knowledgeBaseRepository
                        .findByStableId(knowledgeBaseStableId)
                        .orElseThrow(() -> new IllegalStateException("admin 默认知识库不存在"));
        if (knowledgeDocumentRepository
                .findByKnowledgeBaseIdAndSourceKeyAndDeletedFalse(knowledgeBase.getId(), SOURCE_KEY)
                .isPresent()) {
            return;
        }

        var resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException("框架介绍知识库资源不存在: " + RESOURCE_PATH);
        }
        try {
            var content = resource.getContentAsByteArray();
            var filename = resource.getFilename();
            var document =
                    permissionExecutionService.runAsOwner(
                            knowledgeBase.getOwnerId(),
                            "admin-framework-knowledge-initialization",
                            () ->
                                    OrgContext.runIfAbsent(
                                            knowledgeBase.getOrgId(),
                                            knowledgeBase.getWorkspaceId(),
                                            () ->
                                                    knowledgeDocumentUploadService.uploadResource(
                                                            knowledgeBase,
                                                            content,
                                                            filename,
                                                            "text/markdown",
                                                            SOURCE_KEY)));
            log.info(
                    "已向 admin 默认知识库导入框架介绍，knowledgeBaseId={}, documentId={}",
                    knowledgeBase.getId(),
                    document.getId());
        } catch (IOException failure) {
            throw new IllegalStateException("读取框架介绍知识库资源失败: " + RESOURCE_PATH, failure);
        }
    }
}
