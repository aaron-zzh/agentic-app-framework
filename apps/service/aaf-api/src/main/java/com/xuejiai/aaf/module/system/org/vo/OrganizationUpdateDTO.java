package com.xuejiai.aaf.module.system.org.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新组织请求。
 *
 * @author AaronZZH & Kiro
 */
public record OrganizationUpdateDTO(@Schema(description = "组织名称") @Size(max = 100) String name) {}
