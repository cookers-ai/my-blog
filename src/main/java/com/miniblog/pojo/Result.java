package com.miniblog.pojo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<E> {
    private Integer code;
    private String msg;
    private E data;

    public static <E> Result<E> success(E data) {
        return new Result<>(0, "操作成功", data);
    }

    public static Result<Void> success() {
        return new Result<>(0, "操作成功", null);
    }

    public static Result<Void> error(String message) {
        return new Result<>(1, message, null);
    }
    public static <T> Result<T> error() {
        return new Result<>(1, "操作失败", null);
    }


}
