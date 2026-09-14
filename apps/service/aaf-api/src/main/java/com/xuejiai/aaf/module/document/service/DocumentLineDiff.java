package com.xuejiai.aaf.module.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentDiffHunk;
import com.xuejiai.aaf.module.document.api.DocumentVersionApi.DocumentDiffLine;

/** 基于不可变全文快照计算行级差异；小文档使用最短编辑序列，大文档使用前后缀降级。 */
final class DocumentLineDiff {

    private static final int CONTEXT_LINES = 3;
    private static final long MAX_MATRIX_CELLS = 4_000_000L;

    private DocumentLineDiff() {}

    static List<DocumentDiffHunk> compare(String baseContent, String targetContent) {
        var base = lines(baseContent);
        var target = lines(targetContent);
        var edits =
                (long) (base.length + 1) * (target.length + 1) <= MAX_MATRIX_CELLS
                        ? lcsEdits(base, target)
                        : prefixSuffixEdits(base, target);
        return hunks(toLines(edits));
    }

    private static String[] lines(String content) {
        return normalize(content).split("\n", -1);
    }

    private static String normalize(String content) {
        return Objects.requireNonNullElse(content, "").replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<Edit> lcsEdits(String[] base, String[] target) {
        var lcs = new int[base.length + 1][target.length + 1];
        for (var oldIndex = base.length - 1; oldIndex >= 0; oldIndex--) {
            for (var newIndex = target.length - 1; newIndex >= 0; newIndex--) {
                lcs[oldIndex][newIndex] =
                        base[oldIndex].equals(target[newIndex])
                                ? lcs[oldIndex + 1][newIndex + 1] + 1
                                : Math.max(
                                        lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1]);
            }
        }
        var edits = new ArrayList<Edit>();
        var oldIndex = 0;
        var newIndex = 0;
        while (oldIndex < base.length && newIndex < target.length) {
            if (base[oldIndex].equals(target[newIndex])) {
                edits.add(new Edit("CONTEXT", base[oldIndex]));
                oldIndex++;
                newIndex++;
            } else if (lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1]) {
                edits.add(new Edit("DELETE", base[oldIndex++]));
            } else {
                edits.add(new Edit("ADD", target[newIndex++]));
            }
        }
        while (oldIndex < base.length) {
            edits.add(new Edit("DELETE", base[oldIndex++]));
        }
        while (newIndex < target.length) {
            edits.add(new Edit("ADD", target[newIndex++]));
        }
        return edits;
    }

    private static List<Edit> prefixSuffixEdits(String[] base, String[] target) {
        var prefix = 0;
        while (prefix < base.length
                && prefix < target.length
                && base[prefix].equals(target[prefix])) {
            prefix++;
        }
        var suffix = 0;
        while (suffix < base.length - prefix
                && suffix < target.length - prefix
                && base[base.length - 1 - suffix].equals(target[target.length - 1 - suffix])) {
            suffix++;
        }
        var edits = new ArrayList<Edit>();
        for (var index = 0; index < prefix; index++) {
            edits.add(new Edit("CONTEXT", base[index]));
        }
        for (var index = prefix; index < base.length - suffix; index++) {
            edits.add(new Edit("DELETE", base[index]));
        }
        for (var index = prefix; index < target.length - suffix; index++) {
            edits.add(new Edit("ADD", target[index]));
        }
        for (var index = suffix; index > 0; index--) {
            edits.add(new Edit("CONTEXT", base[base.length - index]));
        }
        return edits;
    }

    private static List<DocumentDiffLine> toLines(List<Edit> edits) {
        var result = new ArrayList<DocumentDiffLine>(edits.size());
        var oldLine = 1;
        var newLine = 1;
        for (var edit : edits) {
            switch (edit.type()) {
                case "CONTEXT" ->
                        result.add(
                                new DocumentDiffLine(
                                        "CONTEXT", oldLine++, newLine++, edit.content()));
                case "DELETE" ->
                        result.add(new DocumentDiffLine("DELETE", oldLine++, null, edit.content()));
                case "ADD" ->
                        result.add(new DocumentDiffLine("ADD", null, newLine++, edit.content()));
                default -> throw new IllegalStateException("未知 Diff 行类型: " + edit.type());
            }
        }
        return result;
    }

    private static List<DocumentDiffHunk> hunks(List<DocumentDiffLine> lines) {
        var changed = new ArrayList<Integer>();
        for (var index = 0; index < lines.size(); index++) {
            if (!"CONTEXT".equals(lines.get(index).type())) {
                changed.add(index);
            }
        }
        if (changed.isEmpty()) {
            return List.of();
        }
        var ranges = new ArrayList<Range>();
        for (var index : changed) {
            var start = Math.max(0, index - CONTEXT_LINES);
            var end = Math.min(lines.size(), index + CONTEXT_LINES + 1);
            if (!ranges.isEmpty() && start <= ranges.getLast().end()) {
                var previous = ranges.removeLast();
                ranges.add(new Range(previous.start(), Math.max(previous.end(), end)));
            } else {
                ranges.add(new Range(start, end));
            }
        }
        var result = new ArrayList<DocumentDiffHunk>();
        for (var range : ranges) {
            var hunkLines = List.copyOf(lines.subList(range.start(), range.end()));
            var oldStart = 1;
            var newStart = 1;
            for (var index = 0; index < range.start(); index++) {
                if (!"ADD".equals(lines.get(index).type())) oldStart++;
                if (!"DELETE".equals(lines.get(index).type())) newStart++;
            }
            var oldCount = (int) hunkLines.stream().filter(line -> line.oldLine() != null).count();
            var newCount = (int) hunkLines.stream().filter(line -> line.newLine() != null).count();
            result.add(new DocumentDiffHunk(oldStart, oldCount, newStart, newCount, hunkLines));
        }
        return List.copyOf(result);
    }

    private record Edit(String type, String content) {}

    private record Range(int start, int end) {}
}
