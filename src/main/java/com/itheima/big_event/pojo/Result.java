package com.itheima.big_event.pojo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
// 统一响应结果类
import lombok.Data;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<E> {
    private Integer code; // 响应码：0 成功，1 失败
    private String msg;   // 响应信息
    private E data;       // 响应数据（泛型，支持不同类型的数据）

    // 快速返回成功响应（带数据）
    public static <E> Result<E> success(E data) {
        return new Result<>(0, "操作成功", data);
    }

    // 快速返回成功响应（不带数据）
    public static Result<Void> success() {
        return new Result<>(0, "操作成功", null);
    }

    // 快速返回失败响应
    public static Result<Void> error(String message) {
        return new Result<>(1, message, null);
    }
    public static <T> Result<T> error() {
        return new Result<>(1, "操作失败", null);
    }


}
