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

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.Computer;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;

@Extension
public final class JCloudsCleanupThread extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(JCloudsCleanupThread.class.getName());

    public JCloudsCleanupThread() {
        super("JClouds slave cleanup");
    }

    @Override
    public long getRecurrencePeriod() {
        return MIN * 5;
    }

    public static void invoke() {
        getInstance().run();
    }

    private static JCloudsCleanupThread getInstance() {
        return Jenkins.get().getExtensionList(AsyncPeriodicWork.class).get(JCloudsCleanupThread.class);
    }

    @Override
    protected Level getNormalLoggingLevel() {
        return Level.FINE;
    }

    @Override
    protected void execute(TaskListener listener) {
        final ImmutableList.Builder<ListenableFuture<?>> deletedNodesBuilder = ImmutableList.<ListenableFuture<?>>builder();
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting);
        final ImmutableList.Builder<JCloudsComputer> computersToDeleteBuilder = ImmutableList.<JCloudsComputer>builder();

        for (final Computer c : Jenkins.get().getComputers()) {
            if (JCloudsComputer.class.isInstance(c)) {
                final JCloudsComputer comp = (JCloudsComputer) c;
                final JCloudsSlave node = comp.getNode();
                if (null != node && node.isPendingDelete()) {
                    computersToDeleteBuilder.add(comp);
                    ListenableFuture<?> f = executor.submit(new Runnable() {
                        public void run() {
                            LOGGER.log(Level.INFO, "Deleting pending node " + comp.getName());
                            try {
                                node.terminate();
                            } catch (IOException e) {
                                LOGGER.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
                            } catch (InterruptedException e) {
                                LOGGER.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
                            }
                        }
                    });
                    deletedNodesBuilder.add(f);
                }
            }
        }

        Futures.getUnchecked(Futures.successfulAsList(deletedNodesBuilder.build()));

        for (JCloudsComputer c : computersToDeleteBuilder.build()) {
            try {
                c.deleteSlave();
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
            }

        }
    }
}
