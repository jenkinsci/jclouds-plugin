package jenkins.plugins.jclouds.compute;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import shaded.com.google.common.base.Joiner;

/**
 * Phone home management.
 */
final class PhoneHomeMonitor {

    private static final Logger LOGGER = Logger.getLogger(PhoneHomeMonitor.class.getName());

    private final Lock phoneHomeLock = new ReentrantLock();
    private final Condition doneWaitPhoneHome = phoneHomeLock.newCondition();

    private boolean isWaiting = false;
    private boolean isInterrupted = false;
    private int waitTimeout = 0;
    private List<String> targets;
    private Thread waitThread = null;

    public PhoneHomeMonitor(final boolean activate, final int timeout) {
        isWaiting = activate;
        waitTimeout = timeout;
    }

    public void join() {
        if (null != waitThread) {
            try {
                waitThread.join();
            } catch (InterruptedException x) {
                LOGGER.info(x.toString());
            }
        }
    }

    public synchronized boolean ring(final String who) {
        boolean ret = targets.remove(who);
        if (targets.isEmpty()) {
            isWaiting = false;
        }
        signalCondition();
        return ret;
    }

    public void ring() {
        isWaiting = false;
        signalCondition();
    }

    public void interrupt() {
        isInterrupted = true;
        signalCondition();
    }

    public synchronized String getTargetString() {
        return null == targets ? "" : Joiner.on(" and ").join(targets);
    }

    private void signalCondition() {
        phoneHomeLock.lock();
        try {
            doneWaitPhoneHome.signal();
        } finally {
            phoneHomeLock.unlock();
        }
    }

    private synchronized void setTargets(final List<String> t) {
        targets = t;
    }

    private void waitCondition(final long millis) throws InterruptedException {
        phoneHomeLock.lock();
        try {
            doneWaitPhoneHome.await(millis, TimeUnit.MILLISECONDS);
        } finally {
            phoneHomeLock.unlock();
        }
    }

    private long getWaitPhoneHomeTimeoutMs() {
        if (0 < waitTimeout) {
            return 60000L * waitTimeout;
        }
        return 0;
    }

    public void waitForPhoneHome(final String who, final PrintStream logger) throws InterruptedException {
        if (null == who || who.isEmpty()) {
            throw new IllegalArgumentException("who may not me null or empty");
        }
        List<String> tmp = new ArrayList<>();
        tmp.add(who);
        setTargets(tmp);
        waitForPhoneHome(logger);
    }

    public void waitForPhoneHome(final List<String> who, final PrintStream logger) {
        if (null == who || who.isEmpty()) {
            throw new IllegalArgumentException("who may not me null or empty");
        }
        if (who.contains(null) || who.contains("")) {
            throw new IllegalArgumentException("who may not may not contain empty targets");
        }
        setTargets(who);
        waitThread = new Thread() {
            @Override
            public void run() {
                try {
                    waitForPhoneHome(logger);
                } catch (InterruptedException x) {
                    LOGGER.info(x.toString());
                }
            }
        };
        waitThread.start();
    }

    private void waitForPhoneHome(PrintStream logger) throws InterruptedException {
        long timeout = System.currentTimeMillis() + getWaitPhoneHomeTimeoutMs();
        while (true) {
            long tdif = timeout - System.currentTimeMillis();
            if (tdif < 0) {
                ring();
                throw new InterruptedException("wait for phone home timed out");
            }
            if (isInterrupted) {
                ring();
                throw new InterruptedException("wait for phone home interrupted");
            }
            if (isWaiting) {
                final String tgs = getTargetString();
                if (!tgs.isEmpty()) {
                    final String msg = "Waiting for " + tgs +
                        " to phone home. " + tdif / 1000 + " seconds until timeout.";
                    LOGGER.info(msg);
                    if (null != logger) {
                        logger.println(msg);
                    }
                    if (tdif > 30000L) {
                        // Wait exactly, but still log a message every 30sec.
                        tdif = 30000L;
                    }
                    waitCondition(tdif);
                }
            } else {
                final String msg = "Finished waiting for phone home";
                LOGGER.info(msg);
                if (null != logger) {
                    logger.println(msg);
                }
                break;
            }
        }
    }
}
