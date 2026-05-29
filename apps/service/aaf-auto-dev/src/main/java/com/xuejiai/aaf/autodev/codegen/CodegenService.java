package com.xuejiai.aaf.autodev.codegen;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.autodev.codegen.dto.EntityDefDTO;
import com.xuejiai.aaf.autodev.codegen.dto.GeneratedFile;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

/** 代码生成核心服务，基于 FreeMarker 模板生成 CRUD 四层代码。 */
@Slf4j
@Service
public class CodegenService {

    /** 字段类型映射：DSL 类型 → Java 类型 */
    private static final Map<String, String> TYPE_MAPPING =
            Map.of(
                    "string", "String",
                    "number", "Long",
                    "boolean", "Boolean",
                    "date", "LocalDateTime",
                    "relationship", "Long");

    private final Configuration freemarkerConfig;

    @Value("${aaf.autodev.codegen.output-dir:./generated}")
    private String outputDir;

    public CodegenService() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_34);
        this.freemarkerConfig.setClassLoaderForTemplateLoading(
                getClass().getClassLoader(), "templates/codegen");
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
    }

    /** 生成代码并写入文件系统。 */
    public List<GeneratedFile> generate(EntityDefDTO def) {
        var files = preview(def);
        files.forEach(this::writeFile);
        log.info("代码生成完成：实体={}, 文件数={}", def.name(), files.size());
        return files;
    }

    /** 预览生成结果，不写入文件。 */
    public List<GeneratedFile> preview(EntityDefDTO def) {
        var model = buildModel(def);
        var layers =
                List.of(
                        new LayerDef(
                                "entity.ftl", "domain", "%s.java".formatted(def.name()), "domain"),
                        new LayerDef(
                                "repository.ftl",
                                "repository",
                                "%sRepository.java".formatted(def.name()),
                                "repository"),
                        new LayerDef(
                                "service.ftl",
                                "service",
                                "%sService.java".formatted(def.name()),
                                "service"),
                        new LayerDef(
                                "controller.ftl",
                                "controller",
                                "%sController.java".formatted(def.name()),
                                "controller"));

        var result = new ArrayList<GeneratedFile>();
        for (var layer : layers) {
            var content = render(layer.template(), model);
            var path = buildPath(def.module(), layer.pkg(), layer.fileName());
            result.add(new GeneratedFile(path, content, layer.layer()));
        }
        return result;
    }

    /** 列出可用模板。 */
    public List<String> listTemplates() {
        return List.of("entity.ftl", "repository.ftl", "service.ftl", "controller.ftl");
    }

    /** 构建模板数据模型。 */
    private Map<String, Object> buildModel(EntityDefDTO def) {
        var model = new HashMap<String, Object>();
        model.put("name", def.name());
        model.put("label", def.label());
        model.put("module", def.module());
        // 转换字段类型
        var fields =
                def.fields().stream()
                        .map(
                                f ->
                                        Map.of(
                                                "name", f.name(),
                                                "label", f.label(),
                                                "javaType",
                                                        TYPE_MAPPING.getOrDefault(
                                                                f.type(), "String"),
                                                "type", f.type(),
                                                "required", f.required(),
                                                "defaultValue",
                                                        f.defaultValue() != null
                                                                ? f.defaultValue()
                                                                : ""))
                        .toList();
        model.put("fields", fields);
        // 包名
        model.put("basePackage", "com.xuejiai.aaf.module.%s".formatted(def.module()));
        return model;
    }

    /** 渲染模板。 */
    private String render(String templateName, Map<String, Object> model) {
        try {
            Template template = freemarkerConfig.getTemplate(templateName);
            var writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "模板渲染失败: " + e.getMessage());
        }
    }

    /** 构建输出文件路径。 */
    private String buildPath(String module, String pkg, String fileName) {
        return "%s/com/xuejiai/aaf/module/%s/%s/%s".formatted(outputDir, module, pkg, fileName);
    }

    /** 写入文件。 */
    private void writeFile(GeneratedFile file) {
        try {
            var path = Path.of(file.path());
            Files.createDirectories(path.getParent());
            Files.writeString(path, file.content());
        } catch (IOException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "文件写入失败: " + e.getMessage());
        }
    }

    /** 层定义。 */
    private record LayerDef(String template, String pkg, String fileName, String layer) {}
}
