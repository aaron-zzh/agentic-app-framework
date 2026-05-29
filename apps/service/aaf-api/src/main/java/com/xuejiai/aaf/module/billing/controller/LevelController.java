package com.xuejiai.aaf.module.billing.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.billing.domain.Level;
import com.xuejiai.aaf.module.billing.service.LevelService;

import lombok.RequiredArgsConstructor;

/** 会员等级接口 */
@RestController
@RequestMapping("/api/billing/level")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    /** 获取所有等级定义 */
    @GetMapping("/list")
    public Result<List<Level>> list() {
        return Result.success(levelService.listAll());
    }

    /** 获取用户当前等级 */
    @GetMapping("/current")
    public Result<Level> current(@RequestParam Long userId) {
        return Result.success(levelService.getCurrentLevel(userId));
    }

    /** 获取用户当前经验值 */
    @GetMapping("/exp")
    public Result<Integer> exp(@RequestParam Long userId) {
        return Result.success(levelService.getExp(userId));
    }
}
