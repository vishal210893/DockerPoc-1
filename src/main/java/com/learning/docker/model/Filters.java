package com.learning.docker.model;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@ToString
@Builder
public class Filters {

    public LocalDateTime startTime;
    public LocalDateTime endTime;

}