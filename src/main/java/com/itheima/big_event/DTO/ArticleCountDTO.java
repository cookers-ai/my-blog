package com.itheima.big_event.DTO;

import lombok.Data;

@Data
public class ArticleCountDTO {
    private Integer viewCount; // 浏览量
    private Integer likeCount; // 点赞数
    private Integer commentCount; // 评论数


}
