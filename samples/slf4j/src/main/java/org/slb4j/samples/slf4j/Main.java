package org.slb4j.samples.slf4j;

import org.slb4j.SLB4J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Main {
    static {
        SLB4J.init();
    }

    private Main() {}

    public static void main(String[] args) {
        Logger logger = LoggerFactory.getLogger(Main.class);
        logger.info("Hello from SLF4J!");
    }
}
