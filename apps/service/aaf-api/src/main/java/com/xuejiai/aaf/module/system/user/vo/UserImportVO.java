package com.xuejiai.aaf.module.system.user.vo;

import org.apache.fesod.sheet.annotation.ExcelProperty;
import org.apache.fesod.sheet.annotation.write.style.ColumnWidth;
import org.apache.fesod.sheet.annotation.write.style.HeadRowHeight;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户导入 VO。兼作导入模板表头定义。
 *
 * @author AaronZZH & Kiro
 */
@Data
@HeadRowHeight(20)
@ColumnWidth(18)
public class UserImportVO {

    @ExcelProperty("用户名")
    @Size(max = 50, message = "用户名最长 50 字符")
    private String username;

    @ExcelProperty("手机号")
    @Size(max = 20, message = "手机号最长 20 字符")
    private String phone;

    @ExcelProperty("昵称")
    @Size(max = 100, message = "昵称最长 100 字符")
    private String nickname;

    @ExcelProperty("密码")
    @Size(min = 6, max = 50, message = "密码长度 6-50")
    private String password;

    @ExcelProperty("状态")
    private Integer status;
}
