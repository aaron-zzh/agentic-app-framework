package com.xuejiai.aaf.module.todo.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum;
import com.xuejiai.aaf.module.todo.domain.Todo;
import com.xuejiai.aaf.module.todo.vo.TodoCreateDTO;

/** 待办创建对象转换器；更新由 Patch 应用，输出由通用视图组装器生成。 */
@Mapper(imports = TodoCategoryEnum.class)
public interface TodoConvert {

    TodoConvert INSTANCE = Mappers.getMapper(TodoConvert.class);

    /**
     * 创建请求转实体。
     *
     * <p>执行人、来源三个字段含业务规则（默认当前用户、来源校验），由 {@code TodoService.toEntity} 单独设置，这里忽略； 分类未传时默认 {@code
     * TodoCategoryEnum.TODO}。
     */
    @Mapping(target = "assigneeId", ignore = true)
    @Mapping(target = "sourceType", ignore = true)
    @Mapping(target = "sourceEntity", ignore = true)
    @Mapping(target = "sourceId", ignore = true)
    @Mapping(
            target = "category",
            source = "category",
            defaultExpression = "java(TodoCategoryEnum.TODO.getCode())")
    Todo toEntity(TodoCreateDTO dto);
}
