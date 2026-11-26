package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.vehicle.dto.stat.StatItem;
import com.campus.vehicle.dto.stat.TrendPoint;
import com.campus.vehicle.entity.AccessRecord;
import com.campus.vehicle.entity.Blacklist;
import com.campus.vehicle.entity.PassCode;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.entity.VisitorApply;
import com.campus.vehicle.mapper.AccessRecordMapper;
import com.campus.vehicle.mapper.BlacklistMapper;
import com.campus.vehicle.mapper.PassCodeMapper;
import com.campus.vehicle.mapper.VehicleMapper;
import com.campus.vehicle.mapper.VisitorApplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter DAY_KEY = DateTimeFormatter.ofPattern("MM-dd");

    private final AccessRecordMapper recordMapper;
    private final PassCodeMapper passMapper;
    private final VisitorApplyMapper applyMapper;
    private final VehicleMapper vehicleMapper;
    private final BlacklistMapper blacklistMapper;

    public Map<String, Object> overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long todayIn = countRecord("IN", "PASS", todayStart);
        long todayOut = countRecord("OUT", "PASS", todayStart);
        long todayReject = countRecord(null, "REJECT", todayStart);

        long onSite = countPass("IN_USE", null);
        long visitorOnSite = countPass("IN_USE", "VISITOR");

        long pendingAudit = countApply("PENDING", null);
        long todayApply = countApply(null, todayStart);
        long todayAuto = countApply("AUTO_APPROVED", todayStart);
        long todayManual = countApply("APPROVED", todayStart);
        long todayRejectApply = countApply("REJECTED", todayStart);

        long approvedVehicle = count(vehicleMapper.selectCount(
                new LambdaQueryWrapper<Vehicle>().eq(Vehicle::getStatus, "APPROVED")));
        long blacklistActive = count(blacklistMapper.selectCount(
                new LambdaQueryWrapper<Blacklist>().eq(Blacklist::getStatus, 1)));

        long approvedTotal = todayAuto + todayManual;
        double autoRate = approvedTotal == 0 ? 0
                : Math.round(todayAuto * 1000.0 / approvedTotal) / 10.0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayIn", todayIn);
        data.put("todayOut", todayOut);
        data.put("todayReject", todayReject);
        data.put("onSite", onSite);
        data.put("visitorOnSite", visitorOnSite);
        data.put("pendingAudit", pendingAudit);
        data.put("todayApply", todayApply);
        data.put("todayAuto", todayAuto);
        data.put("todayManual", todayManual);
        data.put("todayRejectApply", todayRejectApply);
        data.put("autoRate", autoRate);
        data.put("approvedVehicle", approvedVehicle);
        data.put("blacklistActive", blacklistActive);
        data.put("serverTime", LocalDateTime.now());
        return data;
    }

    /** 最近 N 天进出趋势，缺失日期补 0 */
    public List<TrendPoint> trend(int days) {
        int span = Math.min(Math.max(days, 3), 90);
        LocalDate startDate = LocalDate.now().minusDays(span - 1L);
        List<TrendPoint> raw = recordMapper.dailyTrend(startDate.atStartOfDay());
        Map<String, TrendPoint> map = raw.stream()
                .collect(Collectors.toMap(TrendPoint::getDate, Function.identity(), (a, b) -> a));

        List<TrendPoint> result = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            LocalDate day = startDate.plusDays(i);
            String key = day.format(DAY_KEY);
            TrendPoint point = map.get(key);
            if (point == null) {
                point = new TrendPoint();
                point.setDate(key);
                point.setInCount(0L);
                point.setOutCount(0L);
                point.setVisitorCount(0L);
            }
            result.add(point);
        }
        return result;
    }

    public List<StatItem> gateDistribution() {
        return recordMapper.gateDistribution(LocalDate.now().atStartOfDay());
    }

    public List<StatItem> hourDistribution() {
        return recordMapper.hourDistribution(LocalDate.now().atStartOfDay());
    }

    public List<StatItem> rejectReasons() {
        return recordMapper.rejectReasons(LocalDate.now().minusDays(7).atStartOfDay());
    }

    private long countRecord(String direction, String result, LocalDateTime start) {
        LambdaQueryWrapper<AccessRecord> qw = new LambdaQueryWrapper<AccessRecord>()
                .ge(AccessRecord::getOccurTime, start);
        if (direction != null) {
            qw.eq(AccessRecord::getDirection, direction);
        }
        if (result != null) {
            qw.eq(AccessRecord::getVerifyResult, result);
        }
        return count(recordMapper.selectCount(qw));
    }

    private long countPass(String status, String passType) {
        LambdaQueryWrapper<PassCode> qw = new LambdaQueryWrapper<PassCode>()
                .eq(PassCode::getStatus, status);
        if (passType != null) {
            qw.eq(PassCode::getPassType, passType);
        }
        return count(passMapper.selectCount(qw));
    }

    private long countApply(String status, LocalDateTime from) {
        LambdaQueryWrapper<VisitorApply> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(VisitorApply::getStatus, status);
        }
        if (from != null) {
            qw.ge(VisitorApply::getCreateTime, from);
        }
        return count(applyMapper.selectCount(qw));
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
