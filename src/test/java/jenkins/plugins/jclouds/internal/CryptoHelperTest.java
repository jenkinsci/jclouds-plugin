package jenkins.plugins.jclouds.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey.DirectEntryPrivateKeySource;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Fritz Elfert
 */
@WithJenkins
class CryptoHelperTest {

    private static final String RSA_2048_PEM = """
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

    private static final String RSA_3072_PEM = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            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
            -----END OPENSSH PRIVATE KEY-----
            """;
    private static final String RSA_4096_PEM = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            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
            -----END OPENSSH PRIVATE KEY-----
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

    @Test
    void testNullCredential() throws Exception {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new CryptoHelper(null));
        assertEquals("Could not get NULL credential", thrown.getMessage());
    }

    @Test
    void testInvalidCredentialId(JenkinsRule r) throws Exception {
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new CryptoHelper("foo"));
        assertEquals(
                "Could not get keypair from credential: java.io.IOException: Credential foo is not available",
                thrown.getMessage());
    }

    @Test
    void testWrongCredentialType(JenkinsRule r) throws Exception {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL,
                        "test-username-pass",
                        "Username / Password credential for testing",
                        "whocares",
                        "whateverIsRequired"));
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> new CryptoHelper("test-username-pass"));
        assertEquals(
                "Could not get keypair from credential: java.io.IOException: Credential test-username-pass is not available",
                thrown.getMessage());
    }

    @Test
    void testWrongCredentialKeyType(JenkinsRule r) throws Exception {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new BasicSSHUserPrivateKey(
                        CredentialsScope.GLOBAL,
                        "test-wrong-keytype",
                        "whocares",
                        new DirectEntryPrivateKeySource(ECDSA_PEM),
                        "",
                        "Wrong private key type for testing"));
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> new CryptoHelper("test-wrong-keytype"));
        assertTrue(
                thrown.getMessage().startsWith("Invalid key type sun.security.ec.ECPrivateKeyImpl@"),
                "exception message starts with \"Invalid key type sun.security.ec.ECPrivateKeyImpl@\"");
    }

    @Test
    void testEncryptDecrypt(JenkinsRule r) throws Exception {
        String plain =
                new String(getClass().getResourceAsStream("loremipsum.bin").readAllBytes(), StandardCharsets.UTF_8);
        assertNotNull(plain);
        Map<String, String> map = Map.of(
                "test-rsa-2048", RSA_2048_PEM,
                "test-rsa-3072", RSA_3072_PEM,
                "test-rsa-4096", RSA_4096_PEM);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String credentialsId = entry.getKey();
            createRsaCredential(entry.getValue(), credentialsId);
            CryptoHelper ch = new CryptoHelper(credentialsId);
            assertNotNull(ch, String.format("CryptoHelper(%s)", credentialsId));
            String crypted = ch.encrypt(plain);
            assertNotNull(crypted, String.format("Encrypted (with %s) output", credentialsId));
            String decrypted = ch.decrypt(crypted);
            assertNotNull(decrypted, String.format("Decrypted (with %s) output", credentialsId));
            assertEquals(plain, decrypted, String.format("Decrypted (with %s) data", credentialsId));
        }
    }

    private void createRsaCredential(String pem, String id) {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new BasicSSHUserPrivateKey(
                        CredentialsScope.GLOBAL,
                        id,
                        "whocares",
                        new DirectEntryPrivateKeySource(pem),
                        "",
                        "RSA key for testing"));
    }
}
