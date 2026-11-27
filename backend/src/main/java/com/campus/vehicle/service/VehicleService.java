package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.common.PageResult;
import com.campus.vehicle.dto.VehicleRequest;
import com.campus.vehicle.entity.Vehicle;
import com.campus.vehicle.mapper.VehicleMapper;
import com.campus.vehicle.security.LoginUser;
import com.campus.vehicle.security.UserContext;
import com.campus.vehicle.util.PlateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VehicleService {

    private final VehicleMapper vehicleMapper;
    private final AuditLogService auditLogService;

    public PageResult<Vehicle> page(int page, int size, String keyword, String status, String type) {
        LambdaQueryWrapper<Vehicle> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(w -> w.like(Vehicle::getPlateNo, kw)
                    .or().like(Vehicle::getOwnerName, kw)
                    .or().like(Vehicle::getOwnerPhone, kw)
                    .or().like(Vehicle::getDept, kw));
        }
        if (StringUtils.hasText(status)) {
            qw.eq(Vehicle::getStatus, status);
        }
        if (StringUtils.hasText(type)) {
            qw.eq(Vehicle::getVehicleType, type);
        }
        qw.orderByDesc(Vehicle::getCreateTime);
        Page<Vehicle> result = vehicleMapper.selectPage(new Page<>(page, size), qw);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public List<Vehicle> approvedList() {
        return vehicleMapper.selectList(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getStatus, "APPROVED")
                .orderByAsc(Vehicle::getPlateNo));
    }

    public Vehicle findByPlate(String plate) {
        return vehicleMapper.selectOne(new LambdaQueryWrapper<Vehicle>()
                .eq(Vehicle::getPlateNo, PlateUtil.normalize(plate)).last("limit 1"));
    }

    @Transactional
    public Vehicle create(VehicleRequest req) {
        String plate = PlateUtil.normalize(req.getPlateNo());
        if (!PlateUtil.isValid(plate)) {
            throw new BizException("车牌号格式不正确，请按行驶证填写，例如 京A12345");
        }
        if (findByPlate(plate) != null) {
            throw new BizException("车牌 " + plate + " 已存在档案，请勿重复登记");
        }
        Vehicle vehicle = new Vehicle();
        fill(vehicle, req, plate);
        vehicle.setStatus("APPROVED");
        vehicle.setAuditorName(UserContext.require().getRealName());
        vehicle.setAuditTime(LocalDateTime.now());
        vehicle.setAuditRemark("后台登记");
        if (vehicle.getValidStart() == null) {
            vehicle.setValidStart(LocalDate.now());
        }
        if (vehicle.getValidEnd() == null) {
            vehicle.setValidEnd(LocalDate.now().plusYears(1));
        }
        vehicleMapper.insert(vehicle);
        auditLogService.log("车辆档案", "新增", "车牌 " + plate + "，车主 " + req.getOwnerName());
        return vehicleMapper.selectById(vehicle.getId());
    }

    @Transactional
    public Vehicle update(Long id, VehicleRequest req) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BizException("车辆档案不存在");
        }
        String plate = PlateUtil.normalize(req.getPlateNo());
        if (!PlateUtil.isValid(plate)) {
            throw new BizException("车牌号格式不正确");
        }
        Vehicle samePlate = findByPlate(plate);
        if (samePlate != null && !samePlate.getId().equals(id)) {
            throw new BizException("车牌 " + plate + " 已被其他档案占用");
        }
        fill(vehicle, req, plate);
        vehicleMapper.updateById(vehicle);
        auditLogService.log("车辆档案", "修改", "车牌 " + plate + "，车主 " + req.getOwnerName());
        return vehicle;
    }

    @Transactional
    public void audit(Long id, boolean approve, String remark) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BizException("车辆档案不存在");
        }
        LoginUser user = UserContext.require();
        vehicle.setStatus(approve ? "APPROVED" : "REJECTED");
        vehicle.setAuditRemark(StringUtils.hasText(remark) ? remark : (approve ? "资料齐全，审核通过" : "资料不符合要求"));
        vehicle.setAuditorName(user.getRealName());
        vehicle.setAuditTime(LocalDateTime.now());
        if (approve) {
            if (vehicle.getValidStart() == null) {
                vehicle.setValidStart(LocalDate.now());
            }
            if (vehicle.getValidEnd() == null) {
                vehicle.setValidEnd(LocalDate.now().plusYears(1));
            }
        }
        vehicleMapper.updateById(vehicle);
        auditLogService.log("车辆档案", approve ? "审核通过" : "审核驳回", "车牌 " + vehicle.getPlateNo());
    }

    @Transactional
    public void changeStatus(Long id, String status) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            throw new BizException("车辆档案不存在");
        }
        if (!List.of("APPROVED", "DISABLED", "PENDING").contains(status)) {
            throw new BizException("不支持的状态变更");
        }
        vehicle.setStatus(status);
        vehicleMapper.updateById(vehicle);
        auditLogService.log("车辆档案", "状态变更", "车牌 " + vehicle.getPlateNo() + " -> " + status);
    }

    @Transactional
    public void delete(Long id) {
        Vehicle vehicle = vehicleMapper.selectById(id);
        if (vehicle == null) {
            return;
        }
        // 出入记录保留作为历史审计，不因车辆档案删除而级联删除。
        vehicleMapper.deleteById(id);
        auditLogService.log("车辆档案", "删除", "车牌 " + vehicle.getPlateNo() + "（保留历史出入记录）");
    }

    private void fill(Vehicle vehicle, VehicleRequest req, String plate) {
        vehicle.setPlateNo(plate);
        vehicle.setPlateColor(StringUtils.hasText(req.getPlateColor()) ? req.getPlateColor() : "蓝牌");
        vehicle.setOwnerName(req.getOwnerName().trim());
        vehicle.setOwnerPhone(req.getOwnerPhone().trim());
        vehicle.setOwnerId(req.getOwnerId());
        vehicle.setVehicleType(req.getVehicleType());
        vehicle.setDept(req.getDept());
        vehicle.setBrandModel(req.getBrandModel());
        vehicle.setValidStart(req.getValidStart());
        vehicle.setValidEnd(req.getValidEnd());
        if (vehicle.getValidStart() != null && vehicle.getValidEnd() != null
                && vehicle.getValidEnd().isBefore(vehicle.getValidStart())) {
            throw new BizException("有效期结束日期不能早于开始日期");
        }
    }
}
