package com.campus.vehicle.dto.stat;

import lombok.Data;

@Data
public class TrendPoint {

    private String date;
    private Long inCount;
    private Long outCount;
    private Long visitorCount;
}
