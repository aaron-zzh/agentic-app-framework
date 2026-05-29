package ${basePackage}.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import ${basePackage}.domain.${name};
import ${basePackage}.service.${name}Service;
import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** ${label}管理接口。 */
@Tag(name = "${label}管理")
@RestController
@RequestMapping("/api/${module}/${name?uncap_first}s")
@RequiredArgsConstructor
public class ${name}Controller {

    private final ${name}Service ${name?uncap_first}Service;

    @Operation(summary = "查询${label}列表")
    @GetMapping
    public Result<List<${name}>> list() {
        return Result.success(${name?uncap_first}Service.list());
    }

    @Operation(summary = "查询${label}详情")
    @GetMapping("/{id}")
    public Result<${name}> getById(@PathVariable Long id) {
        return Result.success(${name?uncap_first}Service.getById(id));
    }

    @Operation(summary = "创建${label}")
    @PostMapping
    public Result<${name}> create(@RequestBody ${name} entity) {
        return Result.success(${name?uncap_first}Service.create(entity));
    }

    @Operation(summary = "更新${label}")
    @PutMapping("/{id}")
    public Result<${name}> update(@PathVariable Long id, @RequestBody ${name} entity) {
        return Result.success(${name?uncap_first}Service.update(id, entity));
    }

    @Operation(summary = "删除${label}")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        ${name?uncap_first}Service.delete(id);
        return Result.success();
    }
}
