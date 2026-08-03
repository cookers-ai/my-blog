package com.miniblog.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleCategory {
    private Long id;

    // 分类名称
    private String name;

    // 排序序号
    private Integer sort;

    // 创建时间
    private LocalDateTime createTime;

    // 是否删除 0-未删 1-已删
    private Integer isDelete;
}
