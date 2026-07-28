package com.itheima.big_event.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface AdminMapper {
    //更新用户状态(封禁/解封)
    @Update("update user set user_status = #{userStatus} where id = #{id}")
    void updateUserStatus(Integer id, Integer userStatus);

    //批量更新用户状态(封禁/解封)
    void updateUserStatuslist(@Param("ids") List<Integer> ids, @Param("userStatus") Integer userStatus);

    //删除指定文章
    @Delete("delete from article where id = #{id}")
    void adminDeleteArticle(Integer id);

    //更新文章状态(草稿/已发布)
    @Update("update article set state = #{state} where id = #{id}")
    void updateArticleState(Integer id, String state);

    void deleteComments(@Param("ids") List<Long> ids);

    @MapKey("article_id")
    Map<Long, Map<String, Object>> countComment(@Param("ids") List<Long> ids);

    @Update("update article set comment_count = comment_count - #{count} where id = #{articleId}")
    void updateArticleCommentCount(@Param("articleId") Long articleId, @Param("count") Integer count);
}
