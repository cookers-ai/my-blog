package com.miniblog.mapper;

import com.miniblog.pojo.Comment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommentMapper {
    //添加评论
    @Insert("insert into comment (user_id, article_id, content, create_time) values (#{userId}, #{articleId}, #{content}, #{createTime})")
    void add(Comment comment);
    //根据文章id查询评论列表（联表查用户名和头像）
    @Select("select c.*, coalesce(nullif(u.nickname,''), u.username) as username, u.user_pic as userPic " +
            "from comment c left join user u on c.user_id = u.id " +
            "where c.article_id = #{articleId} order by c.create_time asc")
    List<Comment> findCommentList(Integer articleId);
    //根据评论id查询评论
    @Select("select * from comment where id = #{id}")
    Comment findById(Long id);
    //删除评论（返回影响行数，0 表示评论不存在或不属于该用户）
    @Delete("delete from comment where id = #{id} and user_id = #{userId}")
    int deleteComment(Long id, Integer userId);
}
