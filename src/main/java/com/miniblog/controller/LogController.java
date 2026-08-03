package com.miniblog.controller;

import com.miniblog.anno.Log;
import com.miniblog.anno.RequireAdmin;
import com.miniblog.pojo.OperationLog;
import com.miniblog.pojo.PageBean;
import com.miniblog.pojo.Result;
import com.miniblog.service.OperationLogQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/log")
@RequireAdmin
public class LogController {
    @Autowired
    private OperationLogQueryService operationLogQueryService;

    @RequestMapping("/query")
    public Result<PageBean<OperationLog>> queryLog(@RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                                   @RequestParam(required = false) String username,
                                                   @RequestParam(required = false) String module) {
        PageBean<OperationLog> pageBean = operationLogQueryService.queryLog(pageNum, pageSize, username, module);
        return Result.success(pageBean);
    }
}
