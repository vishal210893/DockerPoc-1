package com.solu_m.com.docker_poc_1;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
public class Filters {

    public LocalDateTime startTime;
    public LocalDateTime endTime;

}