package jenkins.plugins.jclouds.internal;

import java.io.IOException;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Fritz Elfert
 */
public class SSHPublicKeyExtractorTest {

    private static final String RSA_PEM = "-----BEGIN RSA PRIVATE KEY-----\n"
        + "MIIEpQIBAAKCAQEAujBFmpi6nyHAK6RBaIkERTO/BGhgZ8h2zoqvT12+mSpjbNRF\n"
        + "YN2oeMH1NsUMYLUdRzFlERqHo/U5pgS9SbXTvUujM153Voh6P+t4d822I2UN7vDc\n"
        + "8MP4JTrUjaApOV0IeS1vem39QmlTXfc1ATFOskp2HoqypSTUv2xor2+OEni26iVx\n"
        + "+CLydTZZpqG0yKUr9XlxWRgHm33kaqe4IP4rSWeOiRKDZTDEhZ5eqIpFfLCugOxz\n"
        + "uUFR60aGgc84tAfbTb6UN1A0+FwWSa1aMl1ohUzEY+j6pIn2AeCHgJ290SxETGRz\n"
        + "JBG2r33/9MAnScWdTlaoepgnp0IK+CfXzCH+uQIDAQABAoIBADOSdWUqEt9LMPil\n"
        + "qbxz32vvtmRZKQL2QvpY7dBVDhtM43dcoM8A9s5kIzEFibUr1a1HoFAJgjLHFS3I\n"
        + "OEo3hCv1zIHJE9MzQHF+HsNIhr/tGNvrebdzAMQHNKL6DxEllNhD3pIR70m69O2d\n"
        + "MOBgsQSvnWI+VtdpiUhwldqqUrcIoWtj0X1KE7R/a0POmiRN7k6GrjreVllixegH\n"
        + "CkPV8eeftQHdCn58S4/qZ8b6vCYXBhPN/cq+CmQ3jfKwcOg18ECFeoot665+SE2K\n"
        + "bJSrI9gBfmMBNPxwUzsP7ehOScuyvwOCJiy3LhzhhhwJPbDelbW3Ts3y27Sk+033\n"
        + "kBKphgECgYEA7sKLjoAMMa3XEAxPiVYx5cAdr862IzSPO3l26ySl9gw+KTQdTc12\n"
        + "BhLDkpd2N+7ME8hOg0nYZI/l8yzR0icEDEAfuwpmWLQPVwdwi4ve4Nu9yFj23rSh\n"
        + "23qduUIIwf9LLUzcRY9Ov+NVxnF+j6u6g7aPrVP8hF/onZ3Vb2mGT3kCgYEAx6Hz\n"
        + "JN/RWv535kM9psmdq+FEKo2TXDSZaDcVJ+78lrE3JpxURKy+tL5GRoavSjJk/WXS\n"
        + "MbDG0ZmRwx8Vzah10d4AKvW13fQHmyxeXn44zPks97hsrDzGMS91G/HycENeCm3s\n"
        + "QLhLZINPO13IBQzQPiC5lzCRqIDrZr1SgYMtGUECgYEArx45xbbdOsLKbpbY714t\n"
        + "Etop6/ytUn0GYRThx+4FW8X3AbmblKkR27p/f1Ff//5B6HCORXUwJfH1Mrq42m6L\n"
        + "ZYDSxRkHoB/Q8IAgZ/ma60nAlOXLi+ToolX4wRxR2BgrR3qMROirVcqj6vzrWu0V\n"
        + "y+1mzDZBi8Xck15kYWcAf+ECgYEAklv5lx9AriXCYd8KZC2Mm2ccQtZpI0Cs9+rq\n"
        + "Z8yfAxwKAxS5819ysbCOdUZpXUx1HhJ4eFXSbfjZFOTFZ3IKb0MDfHuISqGOsgVl\n"
        + "aoG/wwcsILHleqFT7NuOUF6iEAxT9fGBNDHplFdwz2WCL7GlOudjKaVCJPffngNP\n"
        + "agRyHAECgYEA0a3Tr+0LpKbbQyKIw7BPcFJ5xY5OLsx3n1OkMptdp5ZsGEe926os\n"
        + "4rhC8FhQaK4qSlwqa+hVTNAdTRrko+cbNaTroBfr9B2/sxSV4C1+m1fwqbRW0GPm\n"
        + "Btm+l178Rt69FuUl0n3ZKZoKYu9z7A4QXBDhDITUM4rPT5wxf++Fqxw=\n"
        + "-----END RSA PRIVATE KEY-----\n";

    private static final String RSA_EXPECTED = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQ"
        + "ABAAABAQC6MEWamLqfIcArpEFoiQRFM78EaGBnyHbOiq9PXb6ZKmNs1EVg3ah4wfU2x"
        + "QxgtR1HMWURGoej9TmmBL1JtdO9S6MzXndWiHo/63h3zbYjZQ3u8Nzww/glOtSNoCk5"
        + "XQh5LW96bf1CaVNd9zUBMU6ySnYeirKlJNS/bGivb44SeLbqJXH4IvJ1NlmmobTIpSv"
        + "1eXFZGAebfeRqp7gg/itJZ46JEoNlMMSFnl6oikV8sK6A7HO5QVHrRoaBzzi0B9tNvp"
        + "Q3UDT4XBZJrVoyXWiFTMRj6PqkifYB4IeAnb3RLERMZHMkEbavff/0wCdJxZ1OVqh6m"
        + "CenQgr4J9fMIf65";

    private static final String DSA_PEM = "-----BEGIN DSA PRIVATE KEY-----\n"
        + "MIIBugIBAAKBgQCkmZ26oY3DJJ0cH/HVy65WNSMxk7cvNYq/AUvoXbgG/8S+2I70\n"
        + "lamh6i0MvIYsUdPawJk8FIZPo+YNrR8kpVhOE9kWaJLLstSun2ieC1kjGbvUQxQK\n"
        + "oQUxVRjWznedev5U3obugItDF68DE3QutCZl8MQN8xHMdJQ8nukR2uXmKQIVAIIt\n"
        + "SCMAywuUDccgUp+AewenV6uXAoGATkKkvR80T/umsZz0kfSeOS7jz+S2/XeGWltv\n"
        + "HtVRKT/WuBDVdi/31SDL3c5bNi2GrOpSoS4VWB1kfOau9BVIAo7poKw8amIgtGo8\n"
        + "jNfF3YqOa0yd8YiWRD75idpMb4x+KrncWTKfdS4HI2EUJt/3Uz+gXdJluUeeiX+T\n"
        + "hZM6LboCgYApl9gsVy3swP5PtBi2qw67IYw1j3JRfaMef4FNelJLsPvz2A0UG+1V\n"
        + "psZ+IT3s2rLqz0euXVSjKK3IJRwmSyBAQcU7hWyntPGVE4WLGgrSjM+M06LIhNL/\n"
        + "E3wu50YRBDvOdN/xusxDjOdAWyZh2qY/Z9CbVix7lwTQus1oEumEMQIUIjKV78Lb\n"
        + "xvh95OCM7fStsl3oXDQ=\n"
        + "-----END DSA PRIVATE KEY-----\n";

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

    @Test
    public void testExtractRSA() throws IOException {
        String pub = SSHPublicKeyExtractor.extract(RSA_PEM, null);
        assertEquals(RSA_EXPECTED, pub);
    }

    @Test
    public void testExtractDSA() throws IOException {
        String pub = SSHPublicKeyExtractor.extract(DSA_PEM, null);
        assertEquals(DSA_EXPECTED, pub);
    }

    @Test
    public void testExtractInvalidPEM1() throws IOException {
        boolean thrown = false;
        try {
            SSHPublicKeyExtractor.extract("", null);
        } catch (IOException e) {
            thrown = true;
            assertEquals("Invalid PEM structure, '-----BEGIN...' missing", e.getMessage());
        }
        assertTrue(thrown);
    }

    @Test
    public void testExtractInvalidPEM2() throws IOException {
        boolean thrown = false;
        try {
            SSHPublicKeyExtractor.extract("-----BEGIN RSA PRIVATE KEY-----", null);
        } catch (IOException e) {
            thrown = true;
            assertEquals("Invalid PEM structure, -----END RSA PRIVATE KEY----- missing", e.getMessage());
        }
        assertTrue(thrown);
    }

    @Test
    public void testExtractInvalidPEM3() throws IOException {
        boolean thrown = false;
        try {
            SSHPublicKeyExtractor.extract("-----BEGIN RSA PRIVATE KEY-----\nfoo\n-----END RSA PRIVATE KEY-----", null);
        } catch (IOException e) {
            thrown = true;
            assertEquals("Invalid PEM structure, no data available", e.getMessage());
        }
        assertTrue(thrown);
    }

    @Test
    public void testExtractInvalidPEM4() throws IOException {
        boolean thrown = false;
        try {
            SSHPublicKeyExtractor.extract("-----BEGIN RSA PRIVATE KEY-----\nVGhpcyBpcyBhIGpva2UhCg==\n-----END RSA PRIVATE KEY-----", null);
        } catch (IOException e) {
            thrown = true;
            assertEquals("Expected DER Sequence, but found type 84", e.getMessage());
        }
        assertTrue(thrown);
    }

}
