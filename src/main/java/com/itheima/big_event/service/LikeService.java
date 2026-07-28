package com.itheima.big_event.service;

public interface LikeService {
    //点赞文章
    void likeArticle(Integer articleId);
    //是否点赞文章
    boolean isLikeArticle(Integer articleId);
}
