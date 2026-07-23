package com.xuejiai.aaf.module.system.task.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;

/** 待办创建对象转换器；更新由 Patch 应用，输出由通用视图组装器生成。 */
@Mapper
public interface TodoConvert {

    TodoConvert INSTANCE = Mappers.getMapper(TodoConvert.class);

    /** 分类未传时默认 {@code TodoCategoryEnum.TODO}。 */
    @Mapping(target = "assigneeId", ignore = true)
    @Mapping(target = "sourceType", ignore = true)
    @Mapping(target = "sourceEntity", ignore = true)
    @Mapping(target = "sourceId", ignore = true)
    @Mapping(
            target = "category",
            expression =
                    "java(dto.category() != null ? dto.category() :"
                            + " com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum.TODO.getCode())")
    Todo toEntity(TodoCreateDTO dto);
}
