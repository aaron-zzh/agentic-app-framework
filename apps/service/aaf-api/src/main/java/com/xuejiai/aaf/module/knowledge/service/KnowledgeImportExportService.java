package com.xuejiai.aaf.module.knowledge.service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.module.knowledge.domain.KnowledgeSegment;
import com.xuejiai.aaf.module.knowledge.repository.KnowledgeSegmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 知识库导入/导出服务——支持 Excel 和 JSON 批量操作。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeImportExportService {

    private final KnowledgeSegmentRepository segmentRepo;
    private final ObjectMapper objectMapper;

    /** JSON 批量导出知识库段落 */
    public List<Map<String, Object>> exportAsJson(Long knowledgeBaseId) {
        var segments = segmentRepo.findByKnowledgeBaseId(knowledgeBaseId);
        return segments.stream()
                .map(
                        s ->
                                Map.<String, Object>of(
                                        "content", s.getContent(),
                                        "position", s.getPosition()))
                .toList();
    }

    /** JSON 批量导入段落 */
    @Transactional
    public int importFromJson(Long knowledgeBaseId, Long documentId, InputStream inputStream) {
        try {
            var items =
                    objectMapper.readValue(
                            inputStream, new TypeReference<List<Map<String, String>>>() {});
            int count = 0;
            for (var item : items) {
                var content = item.get("content");
                if (content == null || content.isBlank()) continue;
                var segment = new KnowledgeSegment();
                segment.setKnowledgeBaseId(knowledgeBaseId);
                segment.setDocumentId(documentId);
                segment.setContent(content);
                segment.setPosition(count);
                segment.setWordCount(content.length());
                segmentRepo.save(segment);
                count++;
            }
            log.info("JSON 导入完成: kbId={} count={}", knowledgeBaseId, count);
            return count;
        } catch (Exception e) {
            throw new RuntimeException("JSON 导入失败: " + e.getMessage(), e);
        }
    }

    /** Excel/CSV 导入（第一列为内容） */
    @Transactional
    public int importFromExcel(Long knowledgeBaseId, Long documentId, InputStream inputStream) {
        try {
            var lines = new String(inputStream.readAllBytes()).lines().toList();
            int count = 0;
            for (int i = 1; i < lines.size(); i++) { // 跳过表头
                var cols = lines.get(i).split("\t|,");
                if (cols.length == 0 || cols[0].isBlank()) continue;
                var segment = new KnowledgeSegment();
                segment.setKnowledgeBaseId(knowledgeBaseId);
                segment.setDocumentId(documentId);
                segment.setContent(cols[0].trim());
                segment.setPosition(count);
                segment.setWordCount(cols[0].trim().length());
                segmentRepo.save(segment);
                count++;
            }
            log.info("Excel/CSV 导入完成: kbId={} count={}", knowledgeBaseId, count);
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Excel 导入失败: " + e.getMessage(), e);
        }
    }
}
