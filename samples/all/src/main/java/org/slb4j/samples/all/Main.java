package org.slb4j.samples.all;

import org.slb4j.SLB4J;

public final class Main {
    static {
        SLB4J.init();
    }

    private Main() {}

    public static void main(String[] args) {
        // JUL
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger("jul.implementation");
        julLogger.info("Message from JUL");

        // JCL
        org.apache.commons.logging.Log jclLogger = org.apache.commons.logging.LogFactory.getLog("jcl.implementation");
        jclLogger.info("Message from JCL");

        // Log4j
        org.apache.logging.log4j.Logger log4jLogger = org.apache.logging.log4j.LogManager.getLogger("log4j.implementation");
        log4jLogger.info("Message from Log4j");

        // SLF4J
        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger("slf4j.implementation");
        slf4jLogger.info("Message from SLF4J");
    }
}
