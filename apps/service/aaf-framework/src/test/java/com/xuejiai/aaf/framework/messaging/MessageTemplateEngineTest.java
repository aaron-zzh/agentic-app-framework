package com.xuejiai.aaf.framework.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** MessageTemplateEngine 单元测试（B19 FreeMarker SSTI 防护）。 */
class MessageTemplateEngineTest {

    private final MessageTemplateEngine engine = new MessageTemplateEngine();

    @Test
    void render_正常变量插值() {
        assertThat(engine.render("你好 ${name}", Map.of("name", "张三"))).isEqualTo("你好 张三");
    }

    /** B19：含 ?new() 实例化任意类的 SSTI 模板必须被拦截（抛异常而非执行）。 */
    @Test
    void render_SSTI模板被拦截() {
        var ssti =
                "<#assign ex=\"freemarker.template.utility.Execute\"?new()>${ex(\"calc\")}";
        assertThatThrownBy(() -> engine.render(ssti, Map.of()))
                .isInstanceOf(RuntimeException.class);
    }
}
