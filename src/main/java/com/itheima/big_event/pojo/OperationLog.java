package com.itheima.big_event.pojo;

import lombok.Data;

import java.time.LocalDateTime;
//操作日志
@Data
public class OperationLog {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String operation;
    private String method;
    private String params;
    private String ip;
    private Integer status;    // 1成功 0失败
    private String errorMsg;
    private Long costTime;
    private LocalDateTime createTime;
}
