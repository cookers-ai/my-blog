package com.miniblog.service;

import java.util.List;

public interface AdminService {
    //更新用户状态(封禁/解封用户)
    void updateUserStatus(Integer id, Integer userStatus);
    //批量更新用户状态
    void updateUserStatuslist(List<Integer> ids, Integer userStatus);
    //管理员删除指定文章
    void adminDeleteArticle(Integer id);
    //更新文章状态(下架/发布)
    void updateArticleState(Integer id, String state);
    //批量删除评论
    void deleteComment(List<Long> ids);
}
