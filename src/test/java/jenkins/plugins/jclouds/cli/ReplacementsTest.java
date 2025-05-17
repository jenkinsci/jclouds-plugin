package jenkins.plugins.jclouds.cli;

import java.io.ByteArrayInputStream;
//import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * @author Fritz Elfert
 */
class ReplacementsTest {

    @Test
    void testOut() throws Exception {
        Replacements r = new Replacements();
        r.add("search", "repl");

        String xml = r.toXML();
        assertThat(xml, containsString("<replacement from=\"search\" to=\"repl\"/>"));
    }

    @Test
    void testIn() throws Exception {
        String rxml = """
            <replacements>
                <replacement from="from" to="to" />
                <replacement from="yes" to="no" />
            </replacements>
            """;

        Replacements r = new Replacements(rxml);

        String xml = r.toXML();
        assertThat(xml, containsString("<replacement from=\"from\" to=\"to\"/>"));
        assertThat(xml, containsString("<replacement from=\"yes\" to=\"no\"/>"));
    }

    @Test
    void testReplace() throws Exception {
        String rxml = """
            <replacements>
                <replacement from="from" to="to" />
                <replacement from="yes" to="no" />
            </replacements>
            """;

        Replacements r = new Replacements(rxml);

        String result = r.replace("Some from foo yes whatever");
        assertThat(result, containsString("Some to foo no whatever"));
    }

    @Test
    void testReplaceXml() throws Exception {
        String rxml = """
            <replacements>
                <replacement from="bd5f7094-d7c8-4bdf-8cd7-cce2e96c4bfb" to="00000000-d7c8-4bdf-8cd7-000000004bfb" />
                <replacement from="14ddac16-775e-4240-a3fe-b240b721a5d6" to="00000000-775e-4240-a3fe-00000000a5d6" />
                <replacement from="/home/jenkins" to="/opt/jenkins" />
            </replacements>
            """;
        Replacements r = new Replacements(rxml);
        String xml = new String(getClass()
                .getResourceAsStream("template-replacement.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        String result = r.replace(xml);
        assertThat(result, containsString("<fsRoot>/opt/jenkins</fsRoot>"));
        assertThat(result, containsString("<credentialsId>00000000-d7c8-4bdf-8cd7-000000004bfb</credentialsId>"));
        assertThat(result, containsString("<fileId>00000000-775e-4240-a3fe-00000000a5d6</fileId>"));
    }
}
