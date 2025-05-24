package jenkins.plugins.jclouds.compute;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static hudson.cli.CLICommandInvoker.Matcher.succeededSilently;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.cli.CLICommandInvoker;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import jenkins.model.Jenkins;
import jenkins.plugins.jclouds.config.ConfigHelper;
import jenkins.plugins.jclouds.config.UserDataBoothook;
import jenkins.plugins.jclouds.config.UserDataInclude;
import jenkins.plugins.jclouds.config.UserDataIncludeOnce;
import jenkins.plugins.jclouds.config.UserDataPartHandler;
import jenkins.plugins.jclouds.config.UserDataScript;
import jenkins.plugins.jclouds.config.UserDataUpstart;
import jenkins.plugins.jclouds.config.UserDataYaml;
import jenkins.plugins.jclouds.internal.CredentialsHelper;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.GlobalConfigFiles;
import org.jenkinsci.plugins.configfiles.xml.XmlConfig;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class UserDataCommandTest {

    private static final String ADMIN = "admin";
    private static final String READER = "reader";

    /**
     * various crendential ids, created in createTestData()
     */
    private String upCredentialsId = "";

    private String rsaCredentialsId = "";
    private String ecdsaCredentialsId = "";

    // Why does this not work with @BeforeEach?
    public void setUp(JenkinsRule j) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .to(ADMIN)
                .grant(Jenkins.READ)
                .everywhere()
                .to(READER));
    }

    @Test
    void testGetUserDataNoParams(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-userdata").invoke();
        assertThat(res, failedWith(2));
        assertThat(res.stderr(), containsString("Argument \"CREDENTIAL\" is required"));
    }

    @Test
    void testGetUserDataNonexistingCredential(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo");
        assertThat(res, failedWith(4));
        assertThat(
                res.stderr(),
                containsString(
                        "Could not get keypair from credential: java.io.IOException: Credential foo is not available"));
    }

    @Test
    void testGetUserDataWrongCredentialsType(JenkinsRule j) throws Exception {
        createTestData(j, false);
        CLICommandInvoker.Result res = new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs(upCredentialsId);
        assertThat(res, failedWith(4));
        assertThat(
                res.stderr(),
                containsString(String.format(
                        "Could not get keypair from credential: java.io.IOException: Credential %s is not available",
                        upCredentialsId)));
    }

    @Test
    void testGetUserDataWrongKeyType(JenkinsRule j) throws Exception {
        createTestData(j, false);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs(ecdsaCredentialsId);
        assertThat(res, failedWith(4));
        assertThat(res.stderr(), containsString("Invalid key type sun.security.ec.ECPrivateKeyImpl@"));
    }

    @Test
    void testGetUserDataEmpty(JenkinsRule j) throws Exception {
        createTestData(j, false);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs(rsaCredentialsId);
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(String.format("<credentialsId>%s", rsaCredentialsId)));
        assertThat(res.stdout(), containsString("<encryptedConfigData>"));
    }

    @Test
    void testGetUserDataUnencrypted(JenkinsRule j) throws Exception {
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("<configData/>"));
    }

    @Test
    void testGetUserDataPermission(JenkinsRule j) throws Exception {
        setUp(j);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").asUser(READER).invokeWithArgs("foo", "--force");
        assertThat(res, failedWith(6));
        assertThat(res.stderr(), containsString("reader is missing the Overall/Administer permission"));
    }

    @Test
    void testGetUserDataAllTypes(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("<configData>"));
        assertThat(res.stdout(), containsString("<name>myUserDataBoothook</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataIncludeOnce</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataInclude</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataPartHandler</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataScript</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataUpstart</name>"));
        assertThat(res.stdout(), containsString("<name>myUserDataYaml</name>"));
        assertThat(res.stdout(), not(containsString("<name>notMyXmlConfig</name>")));
    }

    @Test
    void testCreateUserDataMerge(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        String xml = res.stdout();

        // Change content of one imported data so that the import is forced to replace the id of the
        // imported data.
        xml = xml.replace("# Not really yaml content", "Perhaps something else");

        List<Config> cfgs = ConfigHelper.getJCloudsConfigs();
        // Get the id of that element in the existing data
        String origId = "unknown";
        for (Config cfg : cfgs) {
            if (cfg.content.equals("# Not really yaml content")) {
                origId = cfg.id;
                break;
            }
        }

        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-userdata")
                .withStdin(stdin)
                .invokeWithArgs("--merge");
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString("<replacements>"));

        List<Config> newcfgs = ConfigHelper.getJCloudsConfigs();
        String newId = "unknown";
        for (Config cfg : newcfgs) {
            if (cfg.content.equals("Perhaps something else")) {
                newId = cfg.id;
                break;
            }
        }
        assertThat(newcfgs.size(), equalTo(cfgs.size() + 1));
        assertThat(res.stdout(), containsString(String.format("<replacement old=\"%s\" new=\"%s\"", origId, newId)));
    }

    @Test
    void testCreateUserDataExisting(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        String xml = res.stdout();

        List<Config> cfgs = ConfigHelper.getJCloudsConfigs();

        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-userdata")
                .withStdin(stdin)
                .invoke();
        assertThat(res, failedWith(4));
        for (Config cfg : cfgs) {
            assertThat(res.stderr(), containsString(String.format("Config data with id %s already exists", cfg.id)));
        }
    }

    @Test
    void testCreateUserDataOverwrite(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        String xml = res.stdout();

        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-userdata")
                .withStdin(stdin)
                .invokeWithArgs("--overwrite");
        assertThat(res, succeededSilently());
    }

    @Test
    void testCreateUserDataBothMergeAndOverwrite(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs("foo", "--force");
        assertThat(res, succeeded());
        String xml = res.stdout();

        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-userdata")
                .withStdin(stdin)
                .invokeWithArgs("--merge", "--overwrite");
        assertThat(res, failedWith(2));
        assertThat(
                res.stderr(),
                anyOf(
                        containsString("ERROR: option \"--merge\" cannot be used with the option(s) [--overwrite]"),
                        containsString("ERROR: option \"--overwrite\" cannot be used with the option(s) [--merge]")));
    }

    @Test
    void testUserDataEncryptDecrypt(JenkinsRule j) throws Exception {
        createTestData(j, true);
        CLICommandInvoker.Result res =
                new CLICommandInvoker(j, "jclouds-get-userdata").invokeWithArgs(rsaCredentialsId);
        assertThat(res, succeeded());
        assertThat(res.stdout(), containsString(String.format("<credentialsId>%s", rsaCredentialsId)));
        assertThat(res.stdout(), containsString("<encryptedConfigData>"));
        String xml = res.stdout();

        // Delete all our config data
        List<Config> cfgs = ConfigHelper.getJCloudsConfigs();
        List<String> origCfgreps = new ArrayList<>();
        int origSize = cfgs.size();
        for (Config cfg : cfgs) {
            origCfgreps.add(cfg.toString());
            GlobalConfigFiles.get().remove(cfg.id);
        }

        InputStream stdin = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        res = new CLICommandInvoker(j, "jclouds-create-userdata")
                .withStdin(stdin)
                .invoke();
        assertThat(res, succeededSilently());

        // Verify that we have regained all our original data
        List<Config> newcfgs = ConfigHelper.getJCloudsConfigs();
        List<String> newCfgreps = new ArrayList<>();
        int newSize = newcfgs.size();

        for (Config cfg : newcfgs) {
            newCfgreps.add(cfg.toString());
        }
        assertThat(newSize, equalTo(origSize));
        assertThat(newCfgreps, equalTo(origCfgreps));
    }

    private static final String RSA_PEM =
            """
            -----BEGIN RSA PRIVATE KEY-----
            MIIEpQIBAAKCAQEAujBFmpi6nyHAK6RBaIkERTO/BGhgZ8h2zoqvT12+mSpjbNRF
            YN2oeMH1NsUMYLUdRzFlERqHo/U5pgS9SbXTvUujM153Voh6P+t4d822I2UN7vDc
            8MP4JTrUjaApOV0IeS1vem39QmlTXfc1ATFOskp2HoqypSTUv2xor2+OEni26iVx
            +CLydTZZpqG0yKUr9XlxWRgHm33kaqe4IP4rSWeOiRKDZTDEhZ5eqIpFfLCugOxz
            uUFR60aGgc84tAfbTb6UN1A0+FwWSa1aMl1ohUzEY+j6pIn2AeCHgJ290SxETGRz
            JBG2r33/9MAnScWdTlaoepgnp0IK+CfXzCH+uQIDAQABAoIBADOSdWUqEt9LMPil
            qbxz32vvtmRZKQL2QvpY7dBVDhtM43dcoM8A9s5kIzEFibUr1a1HoFAJgjLHFS3I
            OEo3hCv1zIHJE9MzQHF+HsNIhr/tGNvrebdzAMQHNKL6DxEllNhD3pIR70m69O2d
            MOBgsQSvnWI+VtdpiUhwldqqUrcIoWtj0X1KE7R/a0POmiRN7k6GrjreVllixegH
            CkPV8eeftQHdCn58S4/qZ8b6vCYXBhPN/cq+CmQ3jfKwcOg18ECFeoot665+SE2K
            bJSrI9gBfmMBNPxwUzsP7ehOScuyvwOCJiy3LhzhhhwJPbDelbW3Ts3y27Sk+033
            kBKphgECgYEA7sKLjoAMMa3XEAxPiVYx5cAdr862IzSPO3l26ySl9gw+KTQdTc12
            BhLDkpd2N+7ME8hOg0nYZI/l8yzR0icEDEAfuwpmWLQPVwdwi4ve4Nu9yFj23rSh
            23qduUIIwf9LLUzcRY9Ov+NVxnF+j6u6g7aPrVP8hF/onZ3Vb2mGT3kCgYEAx6Hz
            JN/RWv535kM9psmdq+FEKo2TXDSZaDcVJ+78lrE3JpxURKy+tL5GRoavSjJk/WXS
            MbDG0ZmRwx8Vzah10d4AKvW13fQHmyxeXn44zPks97hsrDzGMS91G/HycENeCm3s
            QLhLZINPO13IBQzQPiC5lzCRqIDrZr1SgYMtGUECgYEArx45xbbdOsLKbpbY714t
            Etop6/ytUn0GYRThx+4FW8X3AbmblKkR27p/f1Ff//5B6HCORXUwJfH1Mrq42m6L
            ZYDSxRkHoB/Q8IAgZ/ma60nAlOXLi+ToolX4wRxR2BgrR3qMROirVcqj6vzrWu0V
            y+1mzDZBi8Xck15kYWcAf+ECgYEAklv5lx9AriXCYd8KZC2Mm2ccQtZpI0Cs9+rq
            Z8yfAxwKAxS5819ysbCOdUZpXUx1HhJ4eFXSbfjZFOTFZ3IKb0MDfHuISqGOsgVl
            aoG/wwcsILHleqFT7NuOUF6iEAxT9fGBNDHplFdwz2WCL7GlOudjKaVCJPffngNP
            agRyHAECgYEA0a3Tr+0LpKbbQyKIw7BPcFJ5xY5OLsx3n1OkMptdp5ZsGEe926os
            4rhC8FhQaK4qSlwqa+hVTNAdTRrko+cbNaTroBfr9B2/sxSV4C1+m1fwqbRW0GPm
            Btm+l178Rt69FuUl0n3ZKZoKYu9z7A4QXBDhDITUM4rPT5wxf++Fqxw=
            -----END RSA PRIVATE KEY-----
            """;

    private static final String ECDSA_PEM =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAaAAAABNlY2RzYS
            1zaGEyLW5pc3RwMjU2AAAACG5pc3RwMjU2AAAAQQQL0Pqfy/oa5tr7GUl8WjHoWNnWozMV
            f/Mlnh7Cdx7d2opVMjkbecfMAXokgTOXsspbT1a+NNqqub94ulCRRWnXAAAAsPCm5Zbwpu
            WWAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAvQ+p/L+hrm2vsZ
            SXxaMehY2dajMxV/8yWeHsJ3Ht3ailUyORt5x8wBeiSBM5eyyltPVr402qq5v3i6UJFFad
            cAAAAhANgjmx/XKEMgxtBlRZw/O5LT3Cj5WJ8COxu0wPGACxhZAAAAFmZlbGZlcnRAZnJp
            dHouZmUudGhpbmsB
            -----END OPENSSH PRIVATE KEY-----""";

    private void createTestData(JenkinsRule j, boolean full) throws Exception {

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "A Username/Passowrd credential", "CredUser", "secretPassword");
        upCredentialsId = CredentialsHelper.storeCredentials(suc);

        suc = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                null,
                "A RSA credential",
                new DirectEntryPrivateKeySource(RSA_PEM),
                "",
                "RSA key for testing");
        rsaCredentialsId = CredentialsHelper.storeCredentials(suc);

        suc = new BasicSSHUserPrivateKey(
                CredentialsScope.GLOBAL,
                null,
                "whocares",
                new DirectEntryPrivateKeySource(ECDSA_PEM),
                "",
                "Wrong private key type for testing");
        ecdsaCredentialsId = CredentialsHelper.storeCredentials(suc);

        if (full) {
            Config cfg = new UserDataYaml(
                    UUID.randomUUID().toString(), "myUserDataYaml", "A yaml file", "# Not really yaml content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataIncludeOnce(
                    UUID.randomUUID().toString(),
                    "myUserDataIncludeOnce",
                    "An includeOnce file",
                    "# Not really includeOnce content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataBoothook(
                    UUID.randomUUID().toString(),
                    "myUserDataBoothook",
                    "A boothook file",
                    "# Not really a boothook content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataPartHandler(
                    UUID.randomUUID().toString(),
                    "myUserDataPartHandler",
                    "A parthandler file",
                    "# Not really parthandler content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataInclude(
                    UUID.randomUUID().toString(),
                    "myUserDataInclude",
                    "An include file",
                    "# Not really include content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataUpstart(
                    UUID.randomUUID().toString(),
                    "myUserDataUpstart",
                    "An upstart file",
                    "# Not really upstart content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);
            cfg = new UserDataScript(
                    UUID.randomUUID().toString(), "myUserDataScript", "A script file", "# Not really script content");
            cfg.setProviderId(cfg.getClass().getName());
            GlobalConfigFiles.get().save(cfg);

            // Next one is not a JClouds config and therefore should not be exported
            cfg = new XmlConfig(
                    UUID.randomUUID().toString(), "notMyXmlConfig", "An xml file", "# Not really xml content");
            GlobalConfigFiles.get().save(cfg);
        }
    }
}
