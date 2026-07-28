package com.itheima.big_event.DTO;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResultDTO {
    private List<?> list;
    private long minTime;
    private Integer offset;

}
