package com.campus.vehicle.dto;

import lombok.Data;

/**
 * 人工审核入参
 */
@Data
public class AuditRequest {

    /** true 通过 / false 驳回 */
    private Boolean approve;

    private String remark;
}
