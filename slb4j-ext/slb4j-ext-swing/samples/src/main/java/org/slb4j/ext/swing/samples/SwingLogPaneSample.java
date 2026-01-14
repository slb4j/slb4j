package org.slb4j.ext.swing.samples;

import org.slb4j.SLB4J;
import org.slb4j.ext.swing.SwingLogPane;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.LoggerFactory;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * This class demonstrates the use of {@link SwingLogPane} to display log messages in a window at runtime.
 */
@SuppressWarnings({"ClassWithMultipleLoggers", "UseOfSystemOutOrSystemErr"})
public class SwingLogPaneSample {

    static {
        SLB4J.init();
    }

    private static final int AVERAGE_SLEEP_MILLIS = 50;
    private static final int LOG_BUFFER_SIZE = 10_000;
    private static final org.slf4j.Logger SLF4J_LOGGER = LoggerFactory.getLogger("SLF4J." + SwingLogPaneSample.class.getName());
    private static final Log JCL_LOGGER = LogFactory.getLog("JCL." + SwingLogPaneSample.class.getName());
    private static final java.util.logging.Logger JUL_LOGGER = java.util.logging.Logger.getLogger("JUL." + SwingLogPaneSample.class.getName());
    private static final org.apache.logging.log4j.Logger LOG4J_LOGGER = org.apache.logging.log4j.LogManager.getLogger("LOG4J." + SwingLogPaneSample.class.getName());
    private final SwingLogPane logPane;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger n = new AtomicInteger();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SwingLogPaneSample::new);
    }

    public SwingLogPaneSample() {
        logPane = new SwingLogPane(LOG_BUFFER_SIZE);

        JFrame frame = new JFrame(getClass().getSimpleName());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setContentPane(logPane);

        // JComponents need to be focusable to receive key events
        logPane.setFocusable(true);

        int width = 1280;
        frame.setSize(width, (int) Math.round(width / 1.618033988)); // use golden ratio
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        startLoggingThreads();
    }

    @SuppressWarnings("BusyWait")
    private void startLoggingThreads() {
        // start threads
        final int numberOfImplementations = 4;
        for (final int implementation : IntStream.range(0, numberOfImplementations).toArray()) {
            Thread thread = new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                while (true) {
                    if (AVERAGE_SLEEP_MILLIS > 0) {
                        long wait = random.nextLong(2L * AVERAGE_SLEEP_MILLIS * numberOfImplementations);
                        try {
                            Thread.sleep(wait);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    int nr = n.incrementAndGet();

                    int bound = switch (implementation) {
                        case 1, 3 -> 6;
                        default -> 5;
                    };

                    int levelInt = random.nextInt(bound);

                    String msg = "Message #%d, imp %s, original integer level %d".formatted(nr, implementation, levelInt);

                    switch (implementation) {
                        case 0 -> {
                            switch (levelInt) {
                                case 0 -> SLF4J_LOGGER.trace(msg);
                                case 1 -> SLF4J_LOGGER.debug(msg);
                                case 2 -> SLF4J_LOGGER.info(msg);
                                case 3 -> SLF4J_LOGGER.warn(msg);
                                case 4 -> SLF4J_LOGGER.error(msg, generateThrowable());
                                default -> throw new IllegalStateException("integer out of range");
                            }
                        }
                        case 1 -> {
                            switch (levelInt) {
                                case 0 -> JUL_LOGGER.finest(msg);
                                case 1 -> JUL_LOGGER.finer(msg);
                                case 2 -> JUL_LOGGER.fine(msg);
                                case 3 -> JUL_LOGGER.info(msg);
                                case 4 -> JUL_LOGGER.warning(msg);
                                case 5 -> JUL_LOGGER.log(java.util.logging.Level.SEVERE, msg, generateThrowable());
                                default -> throw new IllegalStateException("integer out of range");
                            }
                        }
                        case 2 -> {
                            switch (levelInt) {
                                case 0 -> LOG4J_LOGGER.trace(msg);
                                case 1 -> LOG4J_LOGGER.debug(msg);
                                case 2 -> LOG4J_LOGGER.info(msg);
                                case 3 -> LOG4J_LOGGER.warn(msg);
                                case 4 -> LOG4J_LOGGER.error(msg, generateThrowable());
                                default -> throw new IllegalStateException("integer out of range");
                            }
                        }
                        case 3 -> {
                            switch (levelInt) {
                                case 0 -> JCL_LOGGER.trace(msg);
                                case 1 -> JCL_LOGGER.debug(msg);
                                case 2 -> JCL_LOGGER.info(msg);
                                case 3 -> JCL_LOGGER.warn(msg);
                                case 4 -> JCL_LOGGER.error(msg, generateThrowable());
                                case 5 -> JCL_LOGGER.fatal(msg, generateThrowable());
                                default -> throw new IllegalStateException("integer out of range");
                            }
                        }
                        default -> throw new IllegalStateException("integer out of range");
                    }

                    int current = n.get();
                    if (current % 100 == 0) {
                        System.err.format("That was %d messages%n", current);
                    } else if (current % 10 == 0) {
                        System.out.format("That was %d messages%n", current);
                    }
                }
            }, "Logger-Thread-" + implementation);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private IllegalStateException generateThrowable() {
        if (random.nextBoolean()) {
            return new IllegalStateException("Why?", new UnsupportedOperationException("Because of me!"));
        } else {
            return new IllegalStateException("What happened?");
        }
    }

}
