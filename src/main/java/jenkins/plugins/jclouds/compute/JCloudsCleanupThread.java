package jenkins.plugins.jclouds.compute;

import java.io.IOException;
import java.util.logging.Level;

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
		return Jenkins.getInstance().getExtensionList(AsyncPeriodicWork.class).get(JCloudsCleanupThread.class);
	}

	@Override
	protected void execute(TaskListener listener) {
		final ImmutableList.Builder<ListenableFuture<?>> deletedNodesBuilder = ImmutableList.<ListenableFuture<?>> builder();
		ListeningExecutorService executor = MoreExecutors.listeningDecorator(Computer.threadPoolForRemoting);

		for (final Computer c : Jenkins.getInstance().getComputers()) {
			if (JCloudsComputer.class.isInstance(c)) {
				if (((JCloudsComputer) c).getNode().isPendingDelete()) {
					ListenableFuture<?> f = executor.submit(new Runnable() {
						public void run() {
							logger.log(Level.INFO, "Deleting pending node " + c.getName());
							try {
								((JCloudsComputer) c).deleteSlave();
							} catch (IOException e) {
                                logger.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
                            } catch (InterruptedException e) {
								logger.log(Level.WARNING, "Failed to disconnect and delete " + c.getName() + ": " + e.getMessage());
							}
						}
					});
					deletedNodesBuilder.add(f);
				}
			}
		}

		Futures.getUnchecked(Futures.successfulAsList(deletedNodesBuilder.build()));
	}
}
