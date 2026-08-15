package com.xuejiai.aaf.module.ai.aigc.configuration.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.xuejiai.aaf.module.ai.aigc.configuration.domain.AigcSnippet;
import com.xuejiai.aaf.module.ai.aigc.configuration.repository.AigcSnippetRepository;
import com.xuejiai.aaf.module.system.org.event.OrganizationCreatedEvent;

import lombok.RequiredArgsConstructor;

/** 为新组织初始化平台内置创作片段。 */
@Component
@RequiredArgsConstructor
public class AigcSnippetProvisioningListener {

    private static final List<BuiltinSnippet> BUILTIN_SNIPPETS =
            List.of(
                    new BuiltinSnippet(
                            "cinematic-lighting",
                            "电影感光影",
                            "风格",
                            "电影级布光，柔和体积光与自然阴影，冷暖色调平衡，层次丰富，高动态范围，画面具有叙事感",
                            null),
                    new BuiltinSnippet(
                            "commercial-product-shot",
                            "商业产品棚拍",
                            "产品",
                            "专业商业产品摄影，主体居中，材质纹理清晰，干净渐变背景，柔光箱反射，高级广告质感，细节锐利",
                            "new_product"),
                    new BuiltinSnippet(
                            "natural-portrait",
                            "自然人像质感",
                            "人像",
                            "自然真实的人像摄影，肤色准确，保留细腻皮肤纹理，眼神清晰，柔和轮廓光，浅景深，背景虚化自然",
                            "personal_ip"),
                    new BuiltinSnippet(
                            "golden-ratio-composition",
                            "黄金比例构图",
                            "构图",
                            "黄金比例构图，视觉焦点明确，前中后景层次分明，主体与留白平衡，引导线自然，画面稳定且富有张力",
                            null),
                    new BuiltinSnippet(
                            "high-quality-details",
                            "高质量细节增强",
                            "画质",
                            "高清细节，边缘干净，纹理真实，光照一致，色彩自然，避免过度锐化与塑料质感，专业级成片质量",
                            null));

    private final AigcSnippetRepository repository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void provision(OrganizationCreatedEvent event) {
        var snippets =
                BUILTIN_SNIPPETS.stream()
                        .filter(
                                builtin ->
                                        !repository.existsByOrgIdAndBuiltinCodeAndDeletedFalse(
                                                event.organizationId(), builtin.code()))
                        .map(builtin -> toEntity(event.organizationId(), builtin))
                        .toList();
        if (!snippets.isEmpty()) {
            repository.saveAll(snippets);
        }
    }

    private AigcSnippet toEntity(Long organizationId, BuiltinSnippet builtin) {
        var snippet = new AigcSnippet();
        snippet.setOrgId(organizationId);
        snippet.setBuiltinCode(builtin.code());
        snippet.setName(builtin.name());
        snippet.setCategory(builtin.category());
        snippet.setContent(builtin.content());
        snippet.setProjectTypeCode(builtin.projectTypeCode());
        snippet.setIsPublic(true);
        return snippet;
    }

    private record BuiltinSnippet(
            String code, String name, String category, String content, String projectTypeCode) {}
}
