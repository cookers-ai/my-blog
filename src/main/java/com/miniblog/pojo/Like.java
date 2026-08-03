package com.miniblog.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Like {
    private Long id;
    private Integer userId;
    private Integer articleId;
    private LocalDateTime createTime;
}
