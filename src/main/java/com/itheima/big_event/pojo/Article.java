package com.itheima.big_event.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.itheima.big_event.anno.State;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    private Integer id;
    @NotEmpty
    @Pattern(regexp = "^\\S{1,20}$")
    //必传并且长度在1-20之间不能为空
    private String title;
    @NotEmpty
    private String content;
    @URL
    private String coverImg;
   @State
    private String state;
    @NotNull
    private Integer categoryId;
    private Integer createUser;
    private Integer viewCount; // 浏览量
    private Integer likeCount; // 点赞数
    private Integer commentCount; // 评论数
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    //用来关联分类名称
    private String categoryName;
    //是否点赞
    @TableField(exist = false)
    private Boolean isLike;
    //作者昵称
    @TableField(exist = false)
    private String authorName;
    //作者头像
    @TableField(exist = false)
    private String authorAvatar;

    @Override
    public String toString() {
        return "Article{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", coverImg='" + coverImg + '\'' +
                ", state='" + state + '\'' +
                ", categoryId=" + categoryId +
                ", createUser=" + createUser +
                ", viewCount=" + viewCount +
                ", likeCount=" + likeCount +
                ", commentCount=" + commentCount +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", categoryName='" + categoryName + '\'' +
                '}';
    }
}
