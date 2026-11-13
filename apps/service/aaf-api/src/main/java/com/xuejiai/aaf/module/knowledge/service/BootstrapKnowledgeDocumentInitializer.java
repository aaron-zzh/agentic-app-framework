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
 * 启动时将 {@code bootstrap/} 下的内置资料登记为各内置知识库归属者的文档库文档。
 *
 * <p>只写文档表，不导入知识库：向量化与实体抽取消耗模型额度，由管理员在知识库页面"从文档库导入"时按需触发。 按来源键幂等：文档已存在时跳过且不更新正文。新增内置资料只需在 {@link
 * #GROUPS} 对应分组追加一项；新增内置知识库时追加一个分组。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class BootstrapKnowledgeDocumentInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";

    /** 内置资料分组：目标知识库、文档类型与资源清单。 */
    private static final List<BootstrapGroup> GROUPS =
            List.of(
                    new BootstrapGroup(
                            "平台向导",
                            UUID.fromString("00000000-0000-0000-0000-000000000301"),
                            "platform-guide",
                            List.of(
                                    new BootstrapResource(
                                            "bootstrap/aaf-framework-introduction.md", "AAF 框架介绍"),
                                    new BootstrapResource(
                                            "bootstrap/aaf-products-and-services.md", "产品与服务介绍"))),
                    new BootstrapGroup(
                            "智慧农业农技",
                            UUID.fromString("20000000-0000-4000-8000-000000000001"),
                            "agri-tech",
                            List.of(
                                    new BootstrapResource(
                                            "bootstrap/agri/tomato-disease-control.md",
                                            "设施番茄主要病虫害防治"),
                                    new BootstrapResource(
                                            "bootstrap/agri/cucumber-cultivation.md", "设施黄瓜栽培管理"),
                                    new BootstrapResource(
                                            "bootstrap/agri/strawberry-cultivation.md", "温室草莓栽培管理"),
                                    new BootstrapResource(
                                            "bootstrap/agri/greenhouse-climate-control.md",
                                            "大棚环境调控指南"))),
                    new BootstrapGroup(
                            "农场经营资料",
                            UUID.fromString("20000000-0000-4000-8000-000000000002"),
                            "agri-ops",
                            List.of(
                                    new BootstrapResource(
                                            "bootstrap/agri/farm-products.md", "农场产品与产地介绍"),
                                    new BootstrapResource(
                                            "bootstrap/agri/booking-and-delivery.md", "采摘与配送须知"),
                                    new BootstrapResource(
                                            "bootstrap/agri/after-sales.md", "售后与常见问题"))));

    private final UserRepository userRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final DocumentSourceApi documentSourceApi;
    private final PermissionExecutionService permissionExecutionService;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername(ADMIN_USERNAME).isEmpty()) {
            log.info("未找到内置 admin，跳过内置知识资料初始化");
            return;
        }
        GROUPS.forEach(this::initialize);
    }

    private void initialize(BootstrapGroup group) {
        var knowledgeBase =
                OrgContext.runIgnoring(
                                () ->
                                        knowledgeBaseRepository.findByStableId(
                                                group.knowledgeBaseId()))
                        .orElse(null);
        if (knowledgeBase == null) {
            log.warn("{}知识库不存在，跳过文档初始化，stableId={}", group.label(), group.knowledgeBaseId());
            return;
        }
        var registered =
                permissionExecutionService.runAsOwner(
                        knowledgeBase.getOwnerId(),
                        "bootstrap-knowledge-document-initialization",
                        () ->
                                OrgContext.runIfAbsent(
                                        knowledgeBase.getOrgId(),
                                        knowledgeBase.getWorkspaceId(),
                                        () -> registerResources(group, knowledgeBase)));
        log.info(
                "{}文档初始化完成，registered={}, skipped={}",
                group.label(),
                registered,
                group.resources().size() - registered);
    }

    private int registerResources(BootstrapGroup group, KnowledgeBase knowledgeBase) {
        var ownerId = knowledgeBase.getOwnerId();
        var registered = 0;
        for (var resource : group.resources()) {
            if (documentSourceApi.findBySourceKey(resource.sourceKey(), ownerId).isPresent()) {
                continue;
            }
            register(group, resource, ownerId);
            registered++;
        }
        return registered;
    }

    private void register(BootstrapGroup group, BootstrapResource resource, Long ownerId) {
        var classpathResource = new ClassPathResource(resource.path());
        if (!classpathResource.exists()) {
            throw new IllegalStateException("内置知识资料不存在: " + resource.path());
        }
        try {
            var content = classpathResource.getContentAsString(StandardCharsets.UTF_8);
            var document =
                    documentSourceApi.register(
                            new SourceDocumentCommand(
                                    null,
                                    resource.title(),
                                    group.documentType(),
                                    resource.sourceKey(),
                                    content,
                                    ownerId));
            log.info(
                    "已登记{}内置资料，path={}, documentId={}",
                    group.label(),
                    resource.path(),
                    document.id());
        } catch (IOException failure) {
            throw new IllegalStateException("读取内置知识资料失败: " + resource.path(), failure);
        }
    }

    /** 内置资料分组：资料登记到目标知识库归属者的文档库。 */
    private record BootstrapGroup(
            String label,
            UUID knowledgeBaseId,
            String documentType,
            List<BootstrapResource> resources) {}

    /** 内置资料：classpath 路径作为文档来源键，保证重复启动幂等。 */
    private record BootstrapResource(String path, String title) {

        String sourceKey() {
            return "classpath:" + path;
        }
    }
}
