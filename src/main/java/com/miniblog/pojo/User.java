package com.miniblog.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor//无参构造方法
@AllArgsConstructor//有参构造方法
public class User {
    @NonNull//不能为空
    private Integer id;
    private  String username;
    @JsonIgnore//让spring返回json时忽略该字段
    private  String password;
    @NotEmpty//必须传递
    @Pattern(regexp = "^\\s{1,10}$")
    private  String  nickname;
    @NotEmpty
    @Email//邮箱格式
    private  String  email;
    private  String userPic;//头像
    private String role;
    private Integer userStatus;
    private long followCount;//关注数
    private long fansCount;//粉丝数
    private LocalDateTime createTime;
    private  LocalDateTime updateTime;
    //关注时间（关注列表用，不映射数据库）
    @TableField(exist = false)
    private LocalDateTime followTime;

}
