package com.miniblog.service;

import com.miniblog.pojo.Comment;
import com.miniblog.pojo.PageBean;

public interface CommentService {
    //添加评论
    void addComment(Comment comment);
    //获取评论列表（分页）
    PageBean<Comment> commentList(Integer articleId, Integer pageNum, Integer pageSize);
    //删除评论
    void deleteComment(Long id);
}
