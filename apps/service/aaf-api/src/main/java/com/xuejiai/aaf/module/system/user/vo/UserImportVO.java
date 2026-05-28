package com.xuejiai.aaf.module.system.user.vo;

import org.apache.fesod.sheet.annotation.ExcelProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户导入 VO。
 *
 * @author AaronZZH & Kiro
 */
@Data
public class UserImportVO {

    @ExcelProperty("用户名")
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名最长 50 字符")
    private String username;

    @ExcelProperty("昵称")
    @Size(max = 100, message = "昵称最长 100 字符")
    private String nickname;

    @ExcelProperty("密码")
    @Size(min = 6, max = 50, message = "密码长度 6-50")
    private String password;

    @ExcelProperty("状态")
    private Integer status;
}
