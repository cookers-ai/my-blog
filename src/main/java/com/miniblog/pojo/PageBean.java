package com.miniblog.pojo;

import lombok.Data;

import java.util.List;

@Data
public class PageBean<T> {
    private long total;//总记录数
    private List<T> items;//当前页记录
    private int page;//当前页码
}
