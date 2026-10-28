package com.orderpilot.common.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/** Human-readable business numbers, e.g. SO2610281432051234. Uniqueness is enforced by DB keys. */
public final class Ids {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyMMddHHmmss");

    private Ids() {
    }

    public static String orderNo() {
        return "SO" + timestamp() + random(6);
    }

    public static String paymentNo() {
        return "PY" + timestamp() + random(6);
    }

    public static String providerTxnId() {
        return "SIM-" + Long.toHexString(System.nanoTime()).toUpperCase() + random(4);
    }

    private static String timestamp() {
        return LocalDateTime.now(ZoneOffset.UTC).format(FMT);
    }

    private static String random(int digits) {
        long bound = (long) Math.pow(10, digits);
        return String.format("%0" + digits + "d", ThreadLocalRandom.current().nextLong(bound));
    }
}
