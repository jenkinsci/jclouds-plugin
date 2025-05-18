/**
 * Misc. helper functions for testing
 */

package jenkins.plugins.jclouds.compute;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource;

import java.util.ArrayList;
import java.util.List;

import jenkins.plugins.jclouds.internal.CredentialsHelper;

import org.htmlunit.WebClientUtil;
import org.htmlunit.html.HtmlDivision;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;

import org.jvnet.hudson.test.JenkinsRule;

class TestHelper {

    public static boolean findTemplate(JenkinsRule j, String cloud, String name) {
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName(cloud);
        if (null == c) {
            return false;
        }
        if (null == c.getTemplate(name)) {
            return false;
        }
        return true;
    }

    private static final String RSA_PEM = """
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

    private static final String ECDSA_PEM = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAaAAAABNlY2RzYS
            1zaGEyLW5pc3RwMjU2AAAACG5pc3RwMjU2AAAAQQQL0Pqfy/oa5tr7GUl8WjHoWNnWozMV
            f/Mlnh7Cdx7d2opVMjkbecfMAXokgTOXsspbT1a+NNqqub94ulCRRWnXAAAAsPCm5Zbwpu
            WWAAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAvQ+p/L+hrm2vsZ
            SXxaMehY2dajMxV/8yWeHsJ3Ht3ailUyORt5x8wBeiSBM5eyyltPVr402qq5v3i6UJFFad
            cAAAAhANgjmx/XKEMgxtBlRZw/O5LT3Cj5WJ8COxu0wPGACxhZAAAAFmZlbGZlcnRAZnJp
            dHouZmUudGhpbmsB
            -----END OPENSSH PRIVATE KEY-----
            """;

    public static String createEcdsaCredential() throws Exception {
        StandardUsernameCredentials suc = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, null, "whocares",
            new DirectEntryPrivateKeySource(ECDSA_PEM), "", "private ecdsa key for testing");
        return CredentialsHelper.storeCredentials(suc);
    }

    public static void addTemplateToCloud(JenkinsRule j, String cloud, String name, String cid) {
        JCloudsCloud c = (JCloudsCloud)j.jenkins.clouds.getByName(cloud);
        if (null != c) {
            final JCloudsSlaveTemplate tpl = new JCloudsSlaveTemplate(name, "imageId", null, "hardwareId",
                1, 512, "osFamily", "osVersion", "locationId", "jclouds-slave-type1 jclouds-type2",
                "Description", null /* initScripId */, 1 /* numExecutors */, false /* stopOnTerminate */,
                "jvmOptions", false /* preExistingJenkinsUser */, null /* fsRoot */, false /* allowSudo */,
                false /* installPrivateKey */, 5 /* overrideRetentionTime */, true /* hasOverrideRetentionTime */,
                0 /* spoolDelayMs */, true /* assignFloatingIp */, false /* waitPhoneHome */, 0 /* waitPhoneHomeTimeout */,
                null /* keyPairName */, true /* assignPublicIp */, "network1_id,network2_id",
                "security_group1,security_group2", cid /* credentialsId */,
                null /* adminCredentialsId */, "NORMAL" /* mode */, true /* useConfigDrive */,
                false /* preemptible */, null /* configDataIds */, "192.168.1.0/24" /* preferredAddress */,
                false /* useJnlp */, false /* jnlpProvisioning */);
            c.addTemplate(tpl);
        }
    }

    public static String createTestCloud(JenkinsRule j, String name) throws Exception {

        StandardUsernameCredentials suc = new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, null, "WhateverDescription", "CredUser", "secretPassword");
        String cid = CredentialsHelper.storeCredentials(suc);

        suc = new BasicSSHUserPrivateKey(CredentialsScope.SYSTEM, "test-rsa-key", "whocares",
            new DirectEntryPrivateKeySource(RSA_PEM), "", "private rsa key or testing");
        String rid = CredentialsHelper.storeCredentials(suc);

        List<JCloudsSlaveTemplate> templates = new ArrayList<>();
        JCloudsCloud cloud = new JCloudsCloud(name, "aws-ec2", cid, rid,
                "http://localhost", 1, CloudInstanceDefaults.DEFAULT_INSTANCE_RETENTION_TIME_IN_MINUTES,
                CloudInstanceDefaults.DEFAULT_ERROR_RETENTION_TIME_IN_MINUTES, 600 * 1000, 600 * 1000, null,
                "foobar", true, templates);
        j.jenkins.clouds.add(cloud);
        return cid;
    }

    public static String createTestCloudWithTemplate(JenkinsRule j, String name) throws Exception {
        String cid = createTestCloud(j, name);
        addTemplateToCloud(j, name, "FooTemplate", cid);
        return cid;
    }

    public static void triggerValidation(HtmlElement el) {
        el.fireEvent("change");
        WebClientUtil.waitForJSExec(el.getPage().getWebClient());
    }

    public static String getFormError(HtmlPage p) {
        HtmlDivision div = p.querySelector("div.error");
        if (null != div) {
            return div.getTextContent();
        }
        return "";
    }
    public static String getFormWarning(HtmlPage p) {
        HtmlDivision div = p.querySelector("div.warning");
        if (null != div) {
            return div.getTextContent();
        }
        return "";
    }
 }
