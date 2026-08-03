package com.miniblog.DTO;

import com.miniblog.pojo.User;
import lombok.Data;

@Data
public class userMessageDTO  {
    private Integer id;
    //昵称
    private  String  nickname;
    //用户名（用于 @username 展示）
    private  String  username;
    //用户头像
    private  String userPic;
    //个人简介（数据库中暂无此字段，预留）
    private  String bio;
    private long followCount;//关注数
    private long fansCount;//粉丝数
    private long articleCount;//文章数
}
