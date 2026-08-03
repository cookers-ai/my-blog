package com.miniblog.DTO;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResultDTO {
    private List<?> list;
    private long minTime;
    private Integer offset;

}
