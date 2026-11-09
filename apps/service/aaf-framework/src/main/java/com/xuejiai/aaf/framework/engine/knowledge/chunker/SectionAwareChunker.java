package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.chunker.SectionOutlineBuilder.OutlineSection;
import com.xuejiai.aaf.framework.engine.knowledge.chunker.SectionOutlineBuilder.PageMark;

import lombok.RequiredArgsConstructor;

/**
 * 按章节分块：块不跨章节，章节内再按知识库分块策略切分。
 *
 * <ul>
 *   <li>章节不超过块大小时单独成块
 *   <li>同一上级下相邻的短章节（不足块大小四分之一）在合计不超过块大小时合并，块的章节路径记为共同上级
 *   <li>超过块大小的章节在章节内切分，重叠只发生在同一章节内
 * </ul>
 *
 * <p>块元数据写入 {@code section_path}（标题以 " > " 连接）与 {@code page}（块起点所在页，仅 PDF）。
 */
@Component
@RequiredArgsConstructor
public class SectionAwareChunker {

    private static final String SECTION_PATH_SEPARATOR = " > ";
    private static final String MERGE_SEPARATOR = "\n\n";

    private final ChunkerFactory chunkerFactory;

    public List<DocumentChunk> chunk(
            List<OutlineSection> sections, ChunkConfig config, Map<String, Object> baseMetadata) {
        var chunker = chunkerFactory.getChunker(config.strategy());
        var chunks = new ArrayList<DocumentChunk>();
        for (var unit : mergeShortSiblings(sections, config.chunkSize())) {
            var texts =
                    unit.text().length() <= config.chunkSize()
                            ? List.of(unit.text())
                            : chunker.chunk(unit.text(), config, Map.of()).stream()
                                    .map(DocumentChunk::content)
                                    .toList();
            var cursor = 0;
            for (var text : texts) {
                // 语义切分的块是章节文本的连续片段，可定位起点以取得页码
                var start = unit.text().indexOf(text, cursor);
                if (start >= 0) {
                    cursor = start + 1;
                }
                var metadata = new HashMap<>(baseMetadata);
                var index = chunks.size();
                metadata.put("chunk_index", index);
                if (!unit.headingPath().isEmpty()) {
                    metadata.put(
                            "section_path",
                            String.join(SECTION_PATH_SEPARATOR, unit.headingPath()));
                }
                var page = unit.pageAt(Math.max(start, 0));
                if (page != null) {
                    metadata.put("page", page);
                }
                chunks.add(new DocumentChunk(text, index, metadata, TokenEstimator.estimate(text)));
            }
        }
        return chunks;
    }

    private List<Unit> mergeShortSiblings(List<OutlineSection> sections, int chunkSize) {
        var shortLimit = chunkSize / 4;
        var units = new ArrayList<Unit>();
        for (var section : sections) {
            var next = Unit.of(section);
            if (!units.isEmpty()) {
                var last = units.getLast();
                if (last.parentPath().equals(next.parentPath())
                        && (last.text().length() < shortLimit || next.text().length() < shortLimit)
                        && last.text().length() + MERGE_SEPARATOR.length() + next.text().length()
                                <= chunkSize) {
                    units.set(units.size() - 1, last.merge(next));
                    continue;
                }
            }
            units.add(next);
        }
        return units;
    }

    /** 分块单元：单个章节或合并后的相邻短章节。 */
    private record Unit(
            List<String> headingPath,
            List<String> parentPath,
            String text,
            List<PageMark> pageMarks) {

        static Unit of(OutlineSection section) {
            return new Unit(
                    section.headingPath(),
                    section.parentPath(),
                    section.text(),
                    section.pageMarks());
        }

        Unit merge(Unit next) {
            var marks = new ArrayList<>(pageMarks);
            var shift = text.length() + MERGE_SEPARATOR.length();
            for (var mark : next.pageMarks()) {
                marks.add(new PageMark(mark.offset() + shift, mark.pageNo()));
            }
            return new Unit(parentPath, parentPath, text + MERGE_SEPARATOR + next.text(), marks);
        }

        Integer pageAt(int offset) {
            return new OutlineSection(headingPath, text, pageMarks).pageAt(offset);
        }
    }
}
