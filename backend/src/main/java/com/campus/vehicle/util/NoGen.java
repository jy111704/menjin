package com.campus.vehicle.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class NoGen {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private NoGen() {
    }

    private static String base() {
        return LocalDateTime.now().format(FMT) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    /** 申请单号 */
    public static String apply() {
        return "VA" + base();
    }

    /** 通行码编号 */
    public static String pass() {
        return "PC" + base();
    }

    /** 出入记录编号 */
    public static String record() {
        return "AR" + base();
    }
}
