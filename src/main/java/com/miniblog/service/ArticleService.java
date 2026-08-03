package com.miniblog.service;

import com.miniblog.pojo.*;

import java.util.List;

public interface ArticleService {
    //添加文章
    Result addArticle(Article article);
    //分页查询文章列表
    PageBean<Article> list(Integer pageNum, Integer pageSize, Integer categoryId, String state);
    //根据文章id查询文章详情
    Article detail(Integer id);
    //更新文章
    void updateArticle(Article article);
    //删除文章
    void deleteArticle(Integer id);

    PageBean<Article> adminList(Integer pageNum, Integer pageSize, Integer categoryId, String userId, String state);

    PageBean<Article> getHomeList(Integer pageNum, Integer pageSize, Integer categoryId, String sort);
//查询所有分类
    List<ArticleCategory> categoryList();
    //实时查询文章点赞数和评论数和浏览量并放到article对象中
    void enrichCounts(Article article);

    Article getArticle(Integer id);
//查询关注文章列表
    Result queryBlogOfFollow(long max, Integer offset);
    //查询某用户已发布文章列表
    PageBean<Article> getUserArticles(Integer userId, Integer pageNum, Integer pageSize);
}
