/*
 * Copyright 2010-2016 Adrian Cole, Andrew Bayer, Fritz Elfert, Marat Mavlyutov, Monty Taylor, Vijay Kiran et. al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jenkins.plugins.jclouds.compute;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

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

    PhoneHomeMonitor(final boolean activate, final int timeout) {
        isWaiting = activate;
        waitTimeout = timeout;
    }

    void join() throws InterruptedException {
        if (null != waitThread) {
            waitThread.join();
        }
    }

    synchronized boolean ring(final String who) {
        boolean ret = targets.remove(who);
        if (targets.isEmpty()) {
            isWaiting = false;
        }
        signalCondition();
        return ret;
    }

    void ring() {
        isWaiting = false;
        signalCondition();
    }

    void interrupt() {
        isInterrupted = true;
        signalCondition();
    }

    private synchronized String getTargetString() {
        return null == targets ? "" : String.join(" and ", targets);
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

    void waitForPhoneHome(final String who, final PrintStream logger) throws InterruptedException {
        if (null == who || who.isEmpty()) {
            throw new IllegalArgumentException("who may not me null or empty");
        }
        List<String> tmp = new ArrayList<>();
        tmp.add(who);
        setTargets(tmp);
        waitForPhoneHome(logger);
    }

    void waitForPhoneHomeMultiple(final List<String> who, final PrintStream logger) {
        if (null == who || who.isEmpty()) {
            throw new IllegalArgumentException("who may not be null or empty");
        }
        if (who.contains(null) || who.contains("")) {
            throw new IllegalArgumentException("who may not contain empty targets");
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
        boolean hasWaitedAtAll = false;
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
                hasWaitedAtAll = true;
                final String tgs = getTargetString();
                if (!tgs.isEmpty()) {
                    final String msg =
                            "Waiting for " + tgs + " to phone home. " + tdif / 1000 + " seconds until timeout.";
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
                if (hasWaitedAtAll) {
                    final String msg = "Finished waiting for phone home";
                    LOGGER.info(msg);
                    if (null != logger) {
                        logger.println(msg);
                    }
                }
                break;
            }
        }
    }
}
