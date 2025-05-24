/*
 * Copyright 2025 Fritz Elfert
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
package jenkins.plugins.jclouds.cli;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import org.kohsuke.args4j.Localizable;

/**
 * Defines localizeable messages for org.kohsuke.args4j.
 *
 * <p>Rant: This only exists, to satisfy org.kohsuke.args4j.CmdLineException
 * and org.kohsuke.args4j.Localizable is incompatible with org.jvnet.localizer.Localizable.
 *
 * @author Fritz Elfert
 */
public enum CliMessages implements Localizable {
    AMBIGUOUS_TEMPLATE,
    DELETED_CAUSE,
    INSTANCE_CAP_REACHED,
    NO_SUCH_NODE_EXISTS,
    NO_SUCH_PROFILE_EXISTS,
    NO_SUCH_TEMPLATE_EXISTS,
    NODE_NOT_FROM_JCLOUDS,
    ONE_OFF_CAUSE;

    public String formatWithLocale(Locale locale, Object... args) {
        ResourceBundle localized = ResourceBundle.getBundle(Messages.class.getName(), locale);
        return MessageFormat.format(localized.getString(name()), args);
    }

    public String format(Object... args) {
        return formatWithLocale(Locale.getDefault(), args);
    }

    public String getText() {
        return format();
    }
}
