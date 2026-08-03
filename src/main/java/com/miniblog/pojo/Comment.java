package com.miniblog.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Comment {
    private Long id;
    private Integer userId;
    private Integer articleId;
    private String content;
    private Long parentId; // 父评论ID，0表示根评论
    private Integer replyUserId; // 回复的用户ID
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 扩展字段，用于返回给前端
    private String username; // 评论人用户名
    private String replyUsername; // 回复的用户用户名
    private String userPic; // 评论人头像
}
