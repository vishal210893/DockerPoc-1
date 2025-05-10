package com.learning.docker;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
public class Filters {

    public LocalDateTime startTime;
    public LocalDateTime endTime;

}