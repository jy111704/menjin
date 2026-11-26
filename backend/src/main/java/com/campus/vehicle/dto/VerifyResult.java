package com.campus.vehicle.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 门岗核验结果
 */
@Data
public class VerifyResult {

    private boolean pass;
    private String message;
    /** IN / OUT */
    private String direction;
    private String plateNo;
    /** VISITOR / INTERNAL */
    private String passType;
    private String holderName;
    private String passNo;
    private Long passId;
    private String recordNo;
    private String gateName;
    private LocalDateTime validStart;
    private LocalDateTime validEnd;
    private LocalDateTime occurTime;
    private String passStatus;
    /** 剩余有效分钟数，负数表示已过期 */
    private Long remainMinutes;
}
