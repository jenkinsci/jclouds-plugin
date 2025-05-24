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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

class Replacements {

    private final List<Replacement> replacements;

    Replacements() {
        replacements = new ArrayList<>();
    }

    Replacements(String xml) {
        XStream xstream = Jenkins.XSTREAM;
        xstream.processAnnotations(Replacement.class);
        xstream.alias("replacements", List.class);
        replacements = (List<Replacement>) xstream.fromXML(xml);
    }

    String toXML() {
        XStream xstream = Jenkins.XSTREAM;
        xstream.processAnnotations(Replacement.class);
        xstream.alias("replacements", List.class);
        return xstream.toXML(replacements);
    }

    void add(String from, String to) {
        replacements.add(new Replacement(from, to));
    }

    String replace(String xml) {
        for (Replacement r : replacements) {
            Pattern p = Pattern.compile(
                    String.format("^(\\s*<\\w+)\s+sha256=\"[0-9a-fA-F]+\">%s(<.*)$", Pattern.quote(r.getFrom())),
                    Pattern.MULTILINE);
            xml = p.matcher(xml)
                    .replaceAll(String.format("$1>%s$2", Matcher.quoteReplacement(r.getTo())))
                    .replace(r.getFrom(), r.getTo());
        }
        return xml;
    }

    @XStreamAlias("replacement")
    static class Replacement {

        @XStreamAsAttribute
        private String from;

        @XStreamAsAttribute
        private String to;

        public Replacement(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public String getTo() {
            return to;
        }
    }
}
