package com.itheima.big_event.utils;

public class RedisConstants {
    //登录用户key前缀
    public static final String LONG_USER_KEY="login:token";
    //token过期时间为30分钟
    public static final Long TOKEN_EXPIRE_TIME=30*60L;
    //文章详情key前缀
    public  static  final String ARTICLE_DETAIL_KEY="articleDetail:";
    //文章详情过期时间为3小时
    public static final Long ARTICLE_DETAIL_EXPIRE_TIME=3*60*60L;
    //文章详情存储空值时，过期时间为1分钟
    public static final Long ARTICLE_DETAIL_EMPTY_EXPIRE_TIME=60L;
    //首页列表key前缀
    public static final String HOME_LIST_KEY="homeList:";
    //首页列表过期时间为1小时
    public static final Long HOME_LIST_EXPIRE_TIME=60*60L;
    //文章详情点赞数和评论数和浏览量key前缀
    public static final String ARTICLE_DETAIL_COUNT_KEY = "articleDetail:count:";
    //文章详情互斥锁key前缀
    public static final String ARTICLE_DETAIL_LOCK_KEY = "articleDetail:lock:";
    //关注中推送粉丝key前缀
    public static final String FANS_KEY_PREFIX = "follow:fan:";
    //收件箱过期时间为10天
    public static final Long FANS_INBOX_EXPIRE_TIME = 10 * 24 * 60 * 60L;
    //关注列表key前缀（ZSet，score=关注时间戳）
    public static final String FOLLOW_LIST_KEY = "follow:list:";
}
