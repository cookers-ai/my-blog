package com.itheima.big_event.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper
public interface LikeMapper {
    //检查是否点赞
    @Select("select count(*) from like_record where user_id = #{userId} and article_id = #{articleId}")
    int selectByUserIdAndArticleId(Integer userId, Integer articleId);
// //取消点赞
    @Delete("delete from like_record where user_id = #{userId} and article_id = #{articleId}")
    void deleteByUserIdAndArticleId(Integer userId, Integer articleId);
// 点赞
    @Insert("insert into like_record(user_id,article_id) values(#{userId},#{articleId})")
    void addByUserIdAndArticleId(Integer userId, Integer articleId);
}
