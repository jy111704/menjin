package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.mapper.AccessRecordMapper;
import com.campus.vehicle.mapper.PassCodeMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecordService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccessRecordMapper recordMapper;
    private final PassCodeMapper passMapper;
    private final AuditLogService auditLogService;

    public PageResult<AccessRecord> page(int page, int size, String keyword, String direction,
                                         String result, String passType, Long gateId,
                                         LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AccessRecord> qw = buildQuery(keyword, direction, result, passType, gateId, startDate, endDate);
        qw.orderByDesc(AccessRecord::getOccurTime);
        Page<AccessRecord> pageResult = recordMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(pageResult.getTotal(), page, size, pageResult.getRecords());
    }

    /** 在场车辆（含访客与校内） */
    public List<Map<String, Object>> onSite() {
        List<PassCode> passes = passMapper.selectList(new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getStatus, "IN_USE")
                .orderByDesc(PassCode::getEnterTime));
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> list = new ArrayList<>();
        for (PassCode pass : passes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", pass.getId());
            row.put("passNo", pass.getPassNo());
            row.put("plateNo", pass.getPlateNo());
            row.put("passType", pass.getPassType());
            row.put("holderName", pass.getHolderName());
            row.put("holderPhone", pass.getHolderPhone());
            row.put("enterTime", pass.getEnterTime() == null ? null : pass.getEnterTime().format(FMT));
            row.put("enterGate", pass.getEnterGate());
            row.put("validEnd", pass.getValidEnd() == null ? null : pass.getValidEnd().format(FMT));
            row.put("stayMinutes", pass.getEnterTime() == null ? 0 : Duration.between(pass.getEnterTime(), now).toMinutes());
            row.put("overtime", pass.getValidEnd() != null && now.isAfter(pass.getValidEnd()));
            list.add(row);
        }
        return list;
    }

    public List<AccessRecord> recent(int limit) {
        Page<AccessRecord> page = recordMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<AccessRecord>().orderByDesc(AccessRecord::getOccurTime));
        return page.getRecords();
    }

    @Transactional
    public void delete(Long id) {
        AccessRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException("记录不存在或已被删除");
        }
        recordMapper.deleteById(id);
        auditLogService.log("出入记录", "删除",
                "删除记录 " + nvl(record.getRecordNo()) + "，车牌 " + nvl(record.getPlateNo()));
    }

    @Transactional
    public int deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = recordMapper.deleteBatchIds(ids);
        if (count > 0) {
            auditLogService.log("出入记录", "批量删除", "删除 " + count + " 条记录");
        }
        return count;
    }

    public void export(String keyword, String direction, String result, String passType, Long gateId,
                       LocalDate startDate, LocalDate endDate, HttpServletResponse response) throws Exception {
        LambdaQueryWrapper<AccessRecord> qw = buildQuery(keyword, direction, result, passType, gateId, startDate, endDate);
        qw.orderByDesc(AccessRecord::getOccurTime).last("limit 20000");
        List<AccessRecord> records = recordMapper.selectList(qw);

        String[] headers = {"记录编号", "车牌号", "车辆类型", "通行方向", "门岗", "核验通道", "核验结果",
                "拦截原因", "操作人", "发生时间", "备注"};
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("出入记录");
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }
            int rowIndex = 1;
            for (AccessRecord r : records) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(nvl(r.getRecordNo()));
                row.createCell(1).setCellValue(nvl(r.getPlateNo()));
                row.createCell(2).setCellValue("VISITOR".equals(r.getPassType()) ? "访客车辆" : "校内车辆");
                row.createCell(3).setCellValue("IN".equals(r.getDirection()) ? "入场" : "出场");
                row.createCell(4).setCellValue(nvl(r.getGateName()));
                row.createCell(5).setCellValue(switch (nvl(r.getChannel())) {
                    case "QR" -> "扫码";
                    case "PLATE" -> "车牌识别";
                    case "SYSTEM" -> "系统自动";
                    default -> "人工";
                });
                row.createCell(6).setCellValue("PASS".equals(r.getVerifyResult()) ? "放行" : "拦截");
                row.createCell(7).setCellValue(nvl(r.getRejectReason()));
                row.createCell(8).setCellValue(nvl(r.getOperatorName()));
                row.createCell(9).setCellValue(r.getOccurTime() == null ? "" : r.getOccurTime().format(FMT));
                row.createCell(10).setCellValue(nvl(r.getRemark()));
            }

            String fileName = "车辆出入记录_" + LocalDate.now() + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        }
        auditLogService.log("出入记录", "导出", "共导出 " + records.size() + " 条记录");
    }

    private LambdaQueryWrapper<AccessRecord> buildQuery(String keyword, String direction, String result,
                                                        String passType, Long gateId,
                                                        LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<AccessRecord> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(AccessRecord::getPlateNo, kw)
                    .or().like(AccessRecord::getRecordNo, kw)
                    .or().like(AccessRecord::getOperatorName, kw));
        }
        if (StringUtils.hasText(direction)) {
            qw.eq(AccessRecord::getDirection, direction);
        }
        if (StringUtils.hasText(result)) {
            qw.eq(AccessRecord::getVerifyResult, result);
        }
        if (StringUtils.hasText(passType)) {
            qw.eq(AccessRecord::getPassType, passType);
        }
        if (gateId != null) {
            qw.eq(AccessRecord::getGateId, gateId);
        }
        if (startDate != null) {
            qw.ge(AccessRecord::getOccurTime, startDate.atStartOfDay());
        }
        if (endDate != null) {
            qw.lt(AccessRecord::getOccurTime, endDate.plusDays(1).atStartOfDay());
        }
        return qw;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
