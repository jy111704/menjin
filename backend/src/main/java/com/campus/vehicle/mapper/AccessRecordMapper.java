package com.campus.vehicle.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.vehicle.dto.stat.StatItem;
import com.campus.vehicle.dto.stat.TrendPoint;
import com.campus.vehicle.entity.AccessRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AccessRecordMapper extends BaseMapper<AccessRecord> {

    @Select("""
            SELECT DATE_FORMAT(occur_time, '%m-%d') AS date,
                   SUM(CASE WHEN direction = 'IN'  AND verify_result = 'PASS' THEN 1 ELSE 0 END) AS inCount,
                   SUM(CASE WHEN direction = 'OUT' AND verify_result = 'PASS' THEN 1 ELSE 0 END) AS outCount,
                   SUM(CASE WHEN direction = 'IN'  AND verify_result = 'PASS' AND pass_type = 'VISITOR' THEN 1 ELSE 0 END) AS visitorCount
            FROM access_record
            WHERE occur_time >= #{start}
            GROUP BY DATE_FORMAT(occur_time, '%m-%d')
            ORDER BY MIN(occur_time)
            """)
    List<TrendPoint> dailyTrend(@Param("start") LocalDateTime start);

    @Select("""
            SELECT IFNULL(gate_name, '未知门岗') AS label, COUNT(*) AS value
            FROM access_record
            WHERE occur_time >= #{start} AND direction = 'IN' AND verify_result = 'PASS'
            GROUP BY gate_name
            ORDER BY value DESC
            """)
    List<StatItem> gateDistribution(@Param("start") LocalDateTime start);

    @Select("""
            SELECT CONCAT(LPAD(HOUR(occur_time), 2, '0'), ':00') AS label, COUNT(*) AS value
            FROM access_record
            WHERE occur_time >= #{start} AND direction = 'IN' AND verify_result = 'PASS'
            GROUP BY CONCAT(LPAD(HOUR(occur_time), 2, '0'), ':00')
            ORDER BY MIN(HOUR(occur_time))
            """)
    List<StatItem> hourDistribution(@Param("start") LocalDateTime start);

    @Select("""
            SELECT IFNULL(reject_reason, '其他') AS label, COUNT(*) AS value
            FROM access_record
            WHERE occur_time >= #{start} AND verify_result = 'REJECT'
            GROUP BY reject_reason
            ORDER BY value DESC
            LIMIT 8
            """)
    List<StatItem> rejectReasons(@Param("start") LocalDateTime start);
}
