package com.itheima.big_event.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Follow {
    private Integer id;
    private Integer followerId;//关注者id
    private Integer followeeId;//被关注者id
    private LocalDateTime createTime;//关注时间

}
