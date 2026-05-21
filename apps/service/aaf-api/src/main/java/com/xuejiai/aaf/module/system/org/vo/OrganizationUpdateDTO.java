package com.xuejiai.aaf.module.system.org.vo;

import jakarta.validation.constraints.Size;

/** 更新组织请求。 */
public record OrganizationUpdateDTO(@Size(max = 100) String name) {}
