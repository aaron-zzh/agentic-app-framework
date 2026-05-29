package ${basePackage}.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ${basePackage}.domain.${name};
import ${basePackage}.repository.${name}Repository;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** ${label}服务。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ${name}Service {

    private final ${name}Repository ${name?uncap_first}Repository;

    public List<${name}> list() {
        return ${name?uncap_first}Repository.findAll();
    }

    public ${name} getById(Long id) {
        return ${name?uncap_first}Repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "${label}不存在"));
    }

    @Transactional
    public ${name} create(${name} entity) {
        return ${name?uncap_first}Repository.save(entity);
    }

    @Transactional
    public ${name} update(Long id, ${name} entity) {
        var existing = getById(id);
<#list fields as field>
        existing.set${field.name?cap_first}(entity.get${field.name?cap_first}());
</#list>
        return ${name?uncap_first}Repository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        ${name?uncap_first}Repository.deleteById(id);
    }
}
