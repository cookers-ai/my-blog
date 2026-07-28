package com.itheima.big_event.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.itheima.big_event.mapper.OperationLogMapper;
import com.itheima.big_event.pojo.OperationLog;
import com.itheima.big_event.pojo.PageBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogService {

        @Autowired
        private OperationLogMapper operationLogMapper;
        //@Async:异步执行，指定线程池为logExecutor
        @Async("logExecutor")
        public void saveLog(OperationLog log) {
            operationLogMapper.insert(log);
        }



}
