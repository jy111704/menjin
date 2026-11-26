package com.campus.vehicle.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.vehicle.common.BizException;
import com.campus.vehicle.entity.SysConfig;
import com.campus.vehicle.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 系统参数。所有自动审核策略都存在数据库里，后台改完立即生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private static final Set<String> BOOL_KEYS = Set.of(
            "audit.auto.enable", "audit.host.required", "audit.blacklist.reject", "gate.plate.auto.open");

    private static final Set<String> INT_KEYS = Set.of(
            "audit.daily.limit", "audit.advance.days", "pass.advance.minutes",
            "pass.max.valid.hours", "pass.overtime.grace");

    private final SysConfigMapper configMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public synchronized void reload() {
        cache.clear();
        List<SysConfig> list = configMapper.selectList(null);
        list.forEach(c -> cache.put(c.getConfigKey(), c.getConfigValue()));
        loaded = true;
        log.info("系统参数已加载，共 {} 项", cache.size());
    }

    private void ensureLoaded() {
        if (!loaded) {
            reload();
        }
    }

    public String get(String key, String def) {
        ensureLoaded();
        String value = cache.get(key);
        return value == null || value.isBlank() ? def : value;
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        return Boolean.parseBoolean(get(key, String.valueOf(def)));
    }

    public Map<String, String> asMap() {
        ensureLoaded();
        return new LinkedHashMap<>(cache);
    }

    public List<SysConfig> list() {
        ensureLoaded();
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getGroupName)
                .orderByAsc(SysConfig::getConfigKey));
    }

    @Transactional
    public void update(String key, String value) {
        SysConfig config = configMapper.selectById(key);
        if (config == null) {
            throw new BizException("参数不存在: " + key);
        }
        String trimmed = value == null ? "" : value.trim();
        validate(key, trimmed);
        config.setConfigValue(trimmed);
        configMapper.updateById(config);
        reload();
    }

    private void validate(String key, String value) {
        if (value.isEmpty()) {
            throw new BizException("参数值不能为空");
        }
        if (BOOL_KEYS.contains(key) && !"true".equals(value) && !"false".equals(value)) {
            throw new BizException("该参数只能是 true 或 false");
        }
        if (INT_KEYS.contains(key)) {
            int v;
            try {
                v = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new BizException("该参数必须是整数");
            }
            if (v < 0 || v > 100000) {
                throw new BizException("参数超出合理范围");
            }
        }
        if (key.startsWith("audit.worktime") && !TIME_PATTERN.matcher(value).matches()) {
            throw new BizException("时间格式应为 HH:mm，例如 08:00");
        }
    }
}
