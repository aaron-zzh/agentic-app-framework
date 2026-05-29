package ${basePackage}.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** ${label}实体。 */
@Getter
@Setter
@Entity
@Table(name = "${module}_${name?uncap_first}")
@SQLDelete(sql = "UPDATE ${module}_${name?uncap_first} SET deleted = TRUE WHERE id = ?")
@SQLRestriction("deleted = FALSE")
public class ${name} extends BaseEntity {
<#list fields as field>

    /** ${field.label} */
<#if field.required>
    @Column(nullable = false)
<#else>
    @Column
</#if>
    private ${field.javaType} ${field.name};
</#list>
}
