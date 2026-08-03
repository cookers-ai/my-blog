package com.miniblog.service;

import com.miniblog.pojo.OperationLog;
import com.miniblog.pojo.PageBean;

public interface OperationLogQueryService {
    PageBean<OperationLog> queryLog(Integer pageNum, Integer pageSize, String username, String module);
}
