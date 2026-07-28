package com.itheima.big_event.service;

import com.itheima.big_event.pojo.OperationLog;
import com.itheima.big_event.pojo.PageBean;

public interface OperationLogQueryService {
    PageBean<OperationLog> queryLog(Integer pageNum, Integer pageSize, String username, String module);
}
