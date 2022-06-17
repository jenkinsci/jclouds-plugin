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
package jenkins.plugins.jclouds.internal;

import java.io.Serializable;
import hudson.model.TaskListener;

public class TaskListenerLogger implements org.jclouds.logging.Logger, Serializable {

    private static final long serialVersionUID = 1L;

    private final TaskListener listener;

    public TaskListenerLogger(TaskListener listener) {
        this.listener = listener;
    }

    public void debug(String message, Object... args) {
        // noop
    }

    public void error(String message, Object... args) {
        listener.fatalError(String.format(message, args));
    }

    public void error(Throwable throwable, String message, Object... args) {
        listener.fatalError(String.format(message, args) + ": " + throwable.getCause());
    }

    public String getCategory() {
        return null;
    }

    public void info(String message, Object... args) {
        listener.getLogger().println(String.format(message, args));
    }

    public boolean isDebugEnabled() {
        return false;
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public boolean isTraceEnabled() {
        return false;
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void trace(String message, Object... args) {
    }

    public void warn(String message, Object... args) {
        listener.error(String.format(message, args));
    }

    public void warn(Throwable throwable, String message, Object... args) {
        listener.error(String.format(message, args) + ": " + throwable.getCause());
    }

}
