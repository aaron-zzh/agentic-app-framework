/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.agentscope.core.util;

import java.lang.reflect.Type;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;

import io.agentscope.core.tool.ToolSchemaModule;
import tools.jackson.databind.JsonNode;

/**
 * Patched JsonSchemaUtils —— 覆盖 jar 内同名类，适配 jsonschema-generator 5.0.0（Jackson 3.x）API。
 *
 * <p><b>为什么必须保留</b>：AgentScope 2.0.2 的 {@code agentscope-core} 用 Jackson 2 的 {@code
 * com.fasterxml.jackson.databind.JsonNode} 与 victools 4 风格的 {@code JacksonModule} 编译，其 BOM 钉 {@code
 * jackson 2.21.1} + {@code jsonschema-generator 4.38.0}；而 AAF 因 Spring AI on Jackson 3 必须钉 victools
 * {@code 5.0.0}，并在 {@code aaf-framework/pom.xml} 排除 agentscope 带来的 victools。两边 API 不兼容，删除本类会 在工具
 * schema 生成时 {@code NoSuchMethodError}。上游未修，不是"RC4 的历史问题"。
 *
 * <p><b>这是「禁兼容层」硬规则的显式例外</b>：本类确实是 classpath shadowing。退出路径是独立坐标的最小 AgentScope fork 或向上游提 Jackson 3
 * / victools 5 兼容 PR，见 2026-09-01 Harness 落地计划决策六；在此之前保留并由工具 schema 相关测试锁定行为。升级 AgentScope
 * 时必须重跑这些测试——上游一旦切到 victools 5，本类应立即删除。
 *
 * @hidden
 */
public class JsonSchemaUtils {

    private static final boolean PROPERTY_REQUIRED_BY_DEFAULT = false;

    private static final SchemaGenerator schemaGenerator;

    static {
        JacksonSchemaModule jacksonModule =
                new JacksonSchemaModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED);

        ToolSchemaModule toolSchemaModule =
                PROPERTY_REQUIRED_BY_DEFAULT
                        ? new ToolSchemaModule()
                        : new ToolSchemaModule(
                                ToolSchemaModule.Option.PROPERTY_REQUIRED_FALSE_BY_DEFAULT);

        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(
                                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .with(jacksonModule)
                        .with(toolSchemaModule)
                        .with(Option.PLAIN_DEFINITION_KEYS)
                        .without(Option.SCHEMA_VERSION_INDICATOR);
        SchemaGeneratorConfig config = configBuilder.build();
        schemaGenerator = new SchemaGenerator(config);
    }

    public static Map<String, Object> generateSchemaFromClass(Class<?> clazz) {
        try {
            JsonNode schemaNode = schemaGenerator.generateSchema(clazz);
            return JsonUtils.getJsonCodec()
                    .convertValue(schemaNode, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JSON schema for " + clazz.getName(), e);
        }
    }

    public static Map<String, Object> generateSchemaFromJsonNode(JsonNode schema) {
        try {
            return JsonUtils.getJsonCodec()
                    .convertValue(schema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JSON schema for schema", e);
        }
    }

    public static Map<String, Object> generateSchemaFromType(Type type) {
        try {
            JsonNode schemaNode = schemaGenerator.generateSchema(type);
            return JsonUtils.getJsonCodec()
                    .convertValue(schemaNode, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate JSON schema for " + type.getTypeName(), e);
        }
    }

    public static <T> T convertToObject(Object data, Class<T> targetClass) {
        if (data == null) {
            throw new IllegalStateException("No structured data available in response");
        }
        try {
            return JsonUtils.getJsonCodec().convertValue(data, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert metadata to " + targetClass.getName(), e);
        }
    }
}
