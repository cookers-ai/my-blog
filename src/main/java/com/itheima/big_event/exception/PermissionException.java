package com.itheima.big_event.exception;

public class PermissionException extends RuntimeException {
    /*
    * 权限不足自定义异常*/
    public PermissionException(String message) {
        //调用父类的构造方法，传递异常信息
        super(message);
    }
}
