package org.slb4j.samples.log4j;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slb4j.SLB4J;

public final class Main {
    static {
        SLB4J.init();
    }

    private Main() {}

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger(Main.class);
        logger.info("Hello from Log4j!");
    }
}
