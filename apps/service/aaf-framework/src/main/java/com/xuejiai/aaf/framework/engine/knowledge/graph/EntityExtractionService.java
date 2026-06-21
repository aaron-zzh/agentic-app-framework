package com.xuejiai.aaf.framework.engine.knowledge.graph;

import java.time.Instant;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/** 实体关系抽取服务，通过 LLM 从文本中抽取三元组并存入 Neo4j */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityExtractionService {

    private final ChatClient.Builder chatClientBuilder;
    private final GraphService graphService;
    private final KnowledgeEntityRepository entityRepository;

    /** 从文本中抽取实体关系三元组 */
    public List<ExtractedTriple> extract(String text, Long knowledgeBaseId, Long documentId) {
        var userPrompt = EntityExtractionPrompt.USER_PROMPT_TEMPLATE.replace("{text}", text);

        var content =
                chatClientBuilder
                        .build()
                        .prompt()
                        .system(EntityExtractionPrompt.SYSTEM_PROMPT)
                        .user(userPrompt)
                        .call()
                        .content();

        try {
            return JsonUtils.parseObject(content, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("LLM 返回内容解析失败，原始内容: {}", content, e);
            return List.of();
        }
    }

    /** 抽取实体关系并保存到 Neo4j */
    public void extractAndSave(String text, Long knowledgeBaseId, Long documentId) {
        var triples = extract(text, knowledgeBaseId, documentId);

        for (var triple : triples) {
            var subject = findOrCreateEntity(triple.subject(), knowledgeBaseId, documentId);
            var object = findOrCreateEntity(triple.object(), knowledgeBaseId, documentId);

            var relation = new KnowledgeRelation();
            relation.setType(triple.predicate());
            relation.setConfidence(triple.confidence());
            relation.setWeight(triple.confidence());
            relation.setSourceDocumentId(documentId);
            relation.setCreatedAt(Instant.now());

            graphService.saveRelation(subject.getId(), object.getId(), relation);
        }

        log.info("从文档 {} 抽取并保存 {} 个三元组到知识库 {}", documentId, triples.size(), knowledgeBaseId);
    }

    /** 查找或创建实体，同知识库内按名称去重 */
    private KnowledgeEntity findOrCreateEntity(String name, Long knowledgeBaseId, Long documentId) {
        return entityRepository
                .findByNameAndKnowledgeBaseId(name, knowledgeBaseId)
                .orElseGet(
                        () -> {
                            var entity = new KnowledgeEntity();
                            entity.setName(name);
                            entity.setKnowledgeBaseId(knowledgeBaseId);
                            entity.setSourceDocumentId(documentId);
                            return graphService.saveEntity(entity);
                        });
    }
}
