package com.xuejiai.aaf.framework.engine.knowledge.chunker;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.engine.knowledge.importer.DocumentSection;

/**
 * 把导入器解析出的段落整理为章节大纲：每节包含标题路径、章节文本（首行为本节标题）与页码位置。
 *
 * <p>导入器给出标题层级（docx 标题样式、Markdown/HTML 标题）时直接使用；全文都没有标题层级时（txt、pdf、未用标题样式的 docx），逐行按编号规则识别标题：不超过 40
 * 字、不以标点结尾、以“第X章”“一、”“（一）”“1.”“1.1”等编号开头。
 *
 * <p>整理前先经 {@link TextNormalizer} 压缩空白；PDF 的版面折行（含跨页）合并回同一句，中文直接连接，英文数字以空格连接。
 */
@Component
public class SectionOutlineBuilder {

    private static final int MAX_HEADING_LENGTH = 40;
    private static final String HEADING_FORBIDDEN_ENDINGS = "。！？!?；;，,：:、";
    private static final String PAGE_NUMBER_KEY = "page_number";

    /** PDF 折行判断用的句末标点。 */
    private static final String SENTENCE_END = "。！？!?；;：:";

    /** 判断句末时跳过的右引号与右括号。 */
    private static final String CLOSING_MARKS = "”’」』）)】》\"'";

    /** 下一行形似标题时，上一行至少达到该长度才视为被版面折断的整行。 */
    private static final int MIN_WRAPPED_LINE_LENGTH = 20;

    /** PDF 页码行：“3”“- 3 -”“第 3 页”“3 / 20”等。 */
    private static final Pattern PAGE_NUMBER_LINE =
            Pattern.compile(
                    "^[-—–]?\\s*(?:第\\s*)?\\d{1,4}\\s*(?:页)?\\s*(?:/\\s*\\d{1,4})?\\s*[-—–]?$");

    private static final String CHINESE_NUMBER = "[一二三四五六七八九十百零〇两]+";
    private static final String ANY_NUMBER = "(?:\\d+|" + CHINESE_NUMBER + ")";

    /** 标题编号与层级，层级只用于确定上下级关系，数值越小越靠上。 */
    private static final List<HeadingPattern> HEADING_PATTERNS =
            List.of(
                    new HeadingPattern(Pattern.compile("^第" + ANY_NUMBER + "[章篇编部]"), 1),
                    new HeadingPattern(Pattern.compile("^第" + ANY_NUMBER + "节"), 2),
                    new HeadingPattern(Pattern.compile("^" + CHINESE_NUMBER + "[、．.]"), 3),
                    new HeadingPattern(Pattern.compile("^[（(]" + CHINESE_NUMBER + "[）)]"), 4),
                    new HeadingPattern(Pattern.compile("^\\d+[.．、](?!\\d)"), 5),
                    new HeadingPattern(Pattern.compile("^\\d+\\.\\d+(?!\\.\\d)"), 6),
                    new HeadingPattern(Pattern.compile("^[（(]\\d+[）)]"), 6),
                    new HeadingPattern(Pattern.compile("^\\d+\\.\\d+\\.\\d+"), 7));

    public List<OutlineSection> build(List<DocumentSection> sections) {
        var explicitHeadings = sections.stream().anyMatch(section -> section.level() > 0);
        var drafts = new ArrayList<Draft>();
        var stack = new ArrayDeque<Heading>();
        var current = new Draft(List.of(), null);
        drafts.add(current);
        // 仅 PDF：上一条正文物理行，用于把折行合并回同一句；跨页同样适用
        String previousLine = null;
        for (var section : sections) {
            var content = TextNormalizer.normalize(section.content());
            var page = pageNumber(section);
            var pdf = page != null;
            // PDF 分页不代表分段，跨页句子按普通换行续接；其余导入器的每个段落之间保留空行
            var separator = pdf ? "\n" : "\n\n";
            if (explicitHeadings) {
                var lines = content.split("\\R", 2);
                if (section.level() > 0 && !lines[0].isBlank()) {
                    current = start(drafts, stack, lines[0].strip(), section.level(), page);
                    if (lines.length > 1) {
                        current.append(lines[1], separator, page);
                    }
                } else {
                    current.append(content, separator, page);
                }
                continue;
            }
            var first = true;
            var lines = content.split("\\R", -1);
            for (var index = 0; index < lines.length; index++) {
                var line = lines[index];
                // PDF 每页首行或末行的单独页码不进入正文，避免被折行合并进句子
                if (pdf
                        && isPageEdge(lines, index)
                        && PAGE_NUMBER_LINE.matcher(line.strip()).matches()) {
                    continue;
                }
                var level = headingLevel(line);
                if (pdf && isWrappedContinuation(previousLine, line, level)) {
                    current.appendContinuation(line, joiner(previousLine, line), page);
                    previousLine = line.strip();
                } else if (level > 0) {
                    current = start(drafts, stack, line.strip(), level, page);
                    previousLine = null;
                } else {
                    current.append(line, first ? separator : "\n", page);
                    previousLine = pdf && !line.isBlank() ? line.strip() : null;
                }
                first = false;
            }
        }
        return finish(drafts);
    }

    /**
     * PDF 折行判断：上一行未以句末标点结束、本身不是标题时，下一行是同一句的续行。
     *
     * <p>下一行形似标题（如以“7.0”开头的折行）时，只有上一行足够长、像是被版面折断的整行，才按续行合并，避免吞掉真正的标题。
     */
    private boolean isWrappedContinuation(String previous, String line, int lineHeadingLevel) {
        if (previous == null || previous.isEmpty() || line.isBlank()) {
            return false;
        }
        if (SENTENCE_END.indexOf(lastMeaningfulChar(previous)) >= 0 || headingLevel(previous) > 0) {
            return false;
        }
        return lineHeadingLevel == 0 || previous.length() >= MIN_WRAPPED_LINE_LENGTH;
    }

    /** 是否为本页第一行或最后一行（内容已规范化，首尾无空行）。 */
    private static boolean isPageEdge(String[] lines, int index) {
        return index == 0 || index == lines.length - 1;
    }

    /** 英文或数字跨行时以空格连接，中文直接连接。 */
    private static String joiner(String previous, String line) {
        var last = previous.charAt(previous.length() - 1);
        var next = line.strip().charAt(0);
        return isAsciiWordChar(last) && isAsciiWordChar(next) ? " " : "";
    }

    private static boolean isAsciiWordChar(char character) {
        return character < 128 && Character.isLetterOrDigit(character);
    }

    /** 忽略行尾的右引号与右括号，取实际结束字符。 */
    private static char lastMeaningfulChar(String text) {
        for (var index = text.length() - 1; index >= 0; index--) {
            var character = text.charAt(index);
            if (CLOSING_MARKS.indexOf(character) < 0) {
                return character;
            }
        }
        return text.charAt(text.length() - 1);
    }

    /** 开始新章节：弹出同级及更低级标题后压入当前标题，章节路径为栈内标题依次连接。 */
    private Draft start(
            List<Draft> drafts, Deque<Heading> stack, String title, int level, Integer page) {
        while (!stack.isEmpty() && stack.peekLast().level() >= level) {
            stack.removeLast();
        }
        stack.addLast(new Heading(title, level));
        var path = stack.stream().map(Heading::title).toList();
        var draft = new Draft(path, title);
        draft.markPage(page);
        drafts.add(draft);
        return draft;
    }

    /** 去掉空章节；只有标题、紧跟其下级章节的标题节丢弃，其标题已体现在下级章节路径中。 */
    private List<OutlineSection> finish(List<Draft> drafts) {
        var result = new ArrayList<OutlineSection>();
        for (var index = 0; index < drafts.size(); index++) {
            var draft = drafts.get(index);
            var text = draft.text.toString().stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            if (!draft.hasBody && index + 1 < drafts.size()) {
                var nextPath = drafts.get(index + 1).path;
                if (nextPath.size() > draft.path.size()
                        && nextPath.subList(0, draft.path.size()).equals(draft.path)) {
                    continue;
                }
            }
            result.add(new OutlineSection(draft.path, text, List.copyOf(draft.pageMarks)));
        }
        return List.copyOf(result);
    }

    int headingLevel(String line) {
        var text = line.strip();
        if (text.isEmpty()
                || text.length() > MAX_HEADING_LENGTH
                || HEADING_FORBIDDEN_ENDINGS.indexOf(text.charAt(text.length() - 1)) >= 0) {
            return 0;
        }
        for (var pattern : HEADING_PATTERNS) {
            if (pattern.pattern().matcher(text).find()) {
                return pattern.level();
            }
        }
        return 0;
    }

    private Integer pageNumber(DocumentSection section) {
        if (section.metadata() == null
                || !(section.metadata().get(PAGE_NUMBER_KEY) instanceof Number number)) {
            return null;
        }
        return number.intValue();
    }

    /**
     * 大纲中的一节。
     *
     * @param headingPath 从顶级到本节的标题，导言部分为空列表
     * @param text 章节文本，有标题时首行为本节标题
     * @param pageMarks 文本内页码变化位置，按偏移升序；非 PDF 文档为空
     */
    public record OutlineSection(List<String> headingPath, String text, List<PageMark> pageMarks) {

        public OutlineSection {
            headingPath = List.copyOf(headingPath);
            pageMarks = List.copyOf(pageMarks);
        }

        /** 上级章节路径，用于判断相邻章节是否同属一个上级。 */
        public List<String> parentPath() {
            return headingPath.isEmpty()
                    ? List.of()
                    : headingPath.subList(0, headingPath.size() - 1);
        }

        /** 文本内指定偏移所在页码；没有页码信息时返回 null。 */
        public Integer pageAt(int offset) {
            Integer page = null;
            for (var mark : pageMarks) {
                if (mark.offset() > offset) {
                    break;
                }
                page = mark.pageNo();
            }
            return page;
        }
    }

    /** 从文本偏移 {@code offset} 起属于第 {@code pageNo} 页。 */
    public record PageMark(int offset, int pageNo) {}

    private record HeadingPattern(Pattern pattern, int level) {}

    private record Heading(String title, int level) {}

    private static final class Draft {
        private final List<String> path;
        private final StringBuilder text = new StringBuilder();
        private final List<PageMark> pageMarks = new ArrayList<>();
        private boolean hasBody;

        private Draft(List<String> path, String title) {
            this.path = path;
            if (title != null) {
                text.append(title);
            }
        }

        /** 追加正文；章节内第一段正文与标题之间只换一行，其余按给定分隔符连接。 */
        private void append(String content, String separator, Integer page) {
            var piece = content.strip();
            if (piece.isEmpty()) {
                if (hasBody && "\n".equals(separator)) {
                    // 逐行模式下的空行保留为段落间隔
                    text.append('\n');
                }
                return;
            }
            if (!text.isEmpty()) {
                text.append(hasBody ? separator : "\n");
            }
            markPage(page);
            text.append(piece);
            hasBody = true;
        }

        /** 把 PDF 折行接回上一行末尾。 */
        private void appendContinuation(String content, String joiner, Integer page) {
            var piece = content.strip();
            if (piece.isEmpty()) {
                return;
            }
            text.append(joiner);
            markPage(page);
            text.append(piece);
            hasBody = true;
        }

        private void markPage(Integer page) {
            if (page == null) {
                return;
            }
            if (pageMarks.isEmpty() || pageMarks.getLast().pageNo() != page) {
                pageMarks.add(new PageMark(text.length(), page));
            }
        }
    }
}
