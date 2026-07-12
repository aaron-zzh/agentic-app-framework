package com.xuejiai.aaf.module.system.task.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.system.task.domain.Todo;
import com.xuejiai.aaf.module.system.task.vo.TodoCreateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoUpdateDTO;
import com.xuejiai.aaf.module.system.task.vo.TodoVO;

/**
 * 待办对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface TodoConvert {

    TodoConvert INSTANCE = Mappers.getMapper(TodoConvert.class);

    TodoVO toVO(Todo todo);

    /** 分类未传时默认 {@code TodoCategoryEnum.TODO}。 */
    @Mapping(
            target = "category",
            expression =
                    "java(dto.category() != null ? dto.category() :"
                            + " com.xuejiai.aaf.common.enums.sys.TodoCategoryEnum.TODO.getCode())")
    Todo toEntity(TodoCreateDTO dto);

    /** 将 DTO 中非 null 字段更新到已有 Todo 实体。 */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateFromDTO(TodoUpdateDTO dto, @MappingTarget Todo todo);
}
