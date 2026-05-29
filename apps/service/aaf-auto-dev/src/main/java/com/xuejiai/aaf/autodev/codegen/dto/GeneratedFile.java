package com.xuejiai.aaf.autodev.codegen.dto;

/** 代码生成结果，包含文件路径、内容和所属层。 */
public record GeneratedFile(String path, String content, String layer) {}
