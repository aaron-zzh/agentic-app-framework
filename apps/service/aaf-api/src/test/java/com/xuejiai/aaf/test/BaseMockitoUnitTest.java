package com.xuejiai.aaf.test;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 纯 Mockito 单元测试基类。
 *
 * <p>继承此类即可使用 {@code @Mock}、{@code @InjectMocks} 等注解，无需手动添加
 * {@code @ExtendWith(MockitoExtension.class)}。
 *
 * <p>适用场景：Service 层、工具类等不依赖 Spring 上下文的单元测试。
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseMockitoUnitTest {}
