package jenkins.plugins.jclouds.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * @author Fritz Elfert
 */
class SSHPublicKeyExtractorTest {

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

    private static final String RSA_EXPECTED = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQ"
            + "ABAAABAQC6MEWamLqfIcArpEFoiQRFM78EaGBnyHbOiq9PXb6ZKmNs1EVg3ah4wfU2x"
            + "QxgtR1HMWURGoej9TmmBL1JtdO9S6MzXndWiHo/63h3zbYjZQ3u8Nzww/glOtSNoCk5"
            + "XQh5LW96bf1CaVNd9zUBMU6ySnYeirKlJNS/bGivb44SeLbqJXH4IvJ1NlmmobTIpSv"
            + "1eXFZGAebfeRqp7gg/itJZ46JEoNlMMSFnl6oikV8sK6A7HO5QVHrRoaBzzi0B9tNvp"
            + "Q3UDT4XBZJrVoyXWiFTMRj6PqkifYB4IeAnb3RLERMZHMkEbavff/0wCdJxZ1OVqh6m"
            + "CenQgr4J9fMIf65";

    private static final String DSA_PEM =
            """
            -----BEGIN DSA PRIVATE KEY-----
            MIIBugIBAAKBgQCkmZ26oY3DJJ0cH/HVy65WNSMxk7cvNYq/AUvoXbgG/8S+2I70
            lamh6i0MvIYsUdPawJk8FIZPo+YNrR8kpVhOE9kWaJLLstSun2ieC1kjGbvUQxQK
            oQUxVRjWznedev5U3obugItDF68DE3QutCZl8MQN8xHMdJQ8nukR2uXmKQIVAIIt
            SCMAywuUDccgUp+AewenV6uXAoGATkKkvR80T/umsZz0kfSeOS7jz+S2/XeGWltv
            HtVRKT/WuBDVdi/31SDL3c5bNi2GrOpSoS4VWB1kfOau9BVIAo7poKw8amIgtGo8
            jNfF3YqOa0yd8YiWRD75idpMb4x+KrncWTKfdS4HI2EUJt/3Uz+gXdJluUeeiX+T
            hZM6LboCgYApl9gsVy3swP5PtBi2qw67IYw1j3JRfaMef4FNelJLsPvz2A0UG+1V
            psZ+IT3s2rLqz0euXVSjKK3IJRwmSyBAQcU7hWyntPGVE4WLGgrSjM+M06LIhNL/
            E3wu50YRBDvOdN/xusxDjOdAWyZh2qY/Z9CbVix7lwTQus1oEumEMQIUIjKV78Lb
            xvh95OCM7fStsl3oXDQ=
            -----END DSA PRIVATE KEY-----
            """;

    private static final String DSA_EXPECTED = "ssh-dss AAAAB3NzaC1kc3MAAACBAKS"
            + "ZnbqhjcMknRwf8dXLrlY1IzGTty81ir8BS+hduAb/xL7YjvSVqaHqLQy8hixR09rAmTw"
            + "Uhk+j5g2tHySlWE4T2RZoksuy1K6faJ4LWSMZu9RDFAqhBTFVGNbOd516/lTehu6Ai0M"
            + "XrwMTdC60JmXwxA3zEcx0lDye6RHa5eYpAAAAFQCCLUgjAMsLlA3HIFKfgHsHp1erlwA"
            + "AAIBOQqS9HzRP+6axnPSR9J45LuPP5Lb9d4ZaW28e1VEpP9a4ENV2L/fVIMvdzls2LYa"
            + "s6lKhLhVYHWR85q70FUgCjumgrDxqYiC0ajyM18Xdio5rTJ3xiJZEPvmJ2kxvjH4qudx"
            + "ZMp91LgcjYRQm3/dTP6Bd0mW5R56Jf5OFkzotugAAAIApl9gsVy3swP5PtBi2qw67IYw"
            + "1j3JRfaMef4FNelJLsPvz2A0UG+1VpsZ+IT3s2rLqz0euXVSjKK3IJRwmSyBAQcU7hWy"
            + "ntPGVE4WLGgrSjM+M06LIhNL/E3wu50YRBDvOdN/xusxDjOdAWyZh2qY/Z9CbVix7lwT"
            + "Qus1oEumEMQ==";

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

    private static final String ECDSA_EXPECTED = "ecdsa-sha2-nistp256 AAAAE2VjZHNhL"
            + "XNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBAvQ+p/L+hrm2vsZSXxaMehY2dajM"
            + "xV/8yWeHsJ3Ht3ailUyORt5x8wBeiSBM5eyyltPVr402qq5v3i6UJFFadc=";

    private static final String ED25519_PEM =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACBIfalPqPWFPLQNy37Jdc7lIVSu9stxBqDGEyZQe1xc7wAAAKAUZQXDFGUF
            wwAAAAtzc2gtZWQyNTUxOQAAACBIfalPqPWFPLQNy37Jdc7lIVSu9stxBqDGEyZQe1xc7w
            AAAEAx4va7Qrfm+Uqf70k/Mjx6KGM95jyJNGGBv84cWRBaJ0h9qU+o9YU8tA3Lfsl1zuUh
            VK72y3EGoMYTJlB7XFzvAAAAFmZlbGZlcnRAZnJpdHouZmUudGhpbmsBAgMEBQYH
            -----END OPENSSH PRIVATE KEY-----""";

    private static final String ED25519_EXPECTED =
            "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIEh9qU+o9YU8tA3Lfsl1zuUhVK72y3EGoMYTJlB7XFzv";

    @Test
    void testExtractRSA() throws Exception {
        String pub = SSHPublicKeyExtractor.extract(RSA_PEM, null);
        assertEquals(RSA_EXPECTED, pub);
    }

    @Test
    void testExtractDSA() throws Exception {
        String pub = SSHPublicKeyExtractor.extract(DSA_PEM, null);
        assertEquals(DSA_EXPECTED, pub);
    }

    @Test
    void testExtractECDSA() throws Exception {
        String pub = SSHPublicKeyExtractor.extract(ECDSA_PEM, null);
        assertEquals(ECDSA_EXPECTED, pub);
    }

    @Test
    void testExtractEDD25519() throws Exception {
        String pub = SSHPublicKeyExtractor.extract(ED25519_PEM, null);
        assertEquals(ED25519_EXPECTED, pub);
    }

    @Test
    void testExtractInvalidPEM1() {
        assertThrows(IOException.class, () -> SSHPublicKeyExtractor.extract("", null));
    }

    @Test
    void testExtractInvalidPEM2() {
        assertThrows(IOException.class, () -> SSHPublicKeyExtractor.extract("-----BEGIN RSA PRIVATE KEY-----", null));
    }

    @Test
    void testExtractInvalidPEM3() {
        assertThrows(
                IOException.class,
                () -> SSHPublicKeyExtractor.extract(
                        "-----BEGIN RSA PRIVATE KEY-----\nfoo\n-----END RSA PRIVATE KEY-----", null));
    }

    @Test
    void testExtractInvalidPEM4() {
        assertThrows(
                IOException.class,
                () -> SSHPublicKeyExtractor.extract(
                        "-----BEGIN RSA PRIVATE KEY-----\nVGhpcyBpcyBhIGpva2UhCg==\n-----END RSA PRIVATE KEY-----",
                        null));
    }
}
