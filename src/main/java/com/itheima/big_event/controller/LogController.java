package com.itheima.big_event.controller;

import com.itheima.big_event.anno.Log;
import com.itheima.big_event.anno.RequireAdmin;
import com.itheima.big_event.pojo.OperationLog;
import com.itheima.big_event.pojo.PageBean;
import com.itheima.big_event.pojo.Result;
import com.itheima.big_event.service.OperationLogQueryService;
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
