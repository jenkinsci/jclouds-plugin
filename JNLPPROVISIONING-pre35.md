# Provisioning JNLP parameters

In jenkins, provisioning JNLP parameters with cloud-instances is a bit of a chicken-egg-problem. Both, the JNLP URL
as well as the necessary secret are derived from the node name which is not known until the VM has already been started.
With v1.18, this plugin introduces a new feature for provisioning JNLP related parameters which solves this problem.
There are two variants:
-   For cloud providers that support custom metadata to be set after instance creation, this metadata will be populated
with three properties:
    -   **X-jar** provides the URL of the agent.jar
    -   **X-url** provides the JNLP URL
    -   **X-sec** provides the secret

    This variant is currently supported by **openstack-nova** and **google-compute-engine** only.
-   For other providers, there is another - slightly more complicated - method: Before creating a cloud instance, jenkins
    generates a nonce, which can be inserted into regular provisioning data by using the placeholder `${JNLP_NONCE}`. On the
    cloud VM, this nonce now can be used for authenticating a POST request to `https://your.jenkins.url/jclouds-jnlp-provision`.
    The auth parameter of that request must be the base64-encoded SHA256 hash of the concatenation of nonce and nodename.
    The reply will be an JSON response, containing the three properties mentioned above.

## Examples for provisioning JNLP parameters

### Ubuntu on google-compute-engine

The following yaml configuration can be used with cloud-init in an Ubuntu VM running on **google-compute-engine**. It
should be saved in jenkins as custom config file **JClouds user data (yaml)** and then used in the advanced section of the
jclouds template:
```yaml
#cloud-config
users:
  - name: jenkins
    gecos: Jenkins Agent
    shell: /bin/bash
packages:
  - openjdk-8-jdk-headless
write_files:
  - path: /usr/local/sbin/start-jnlpagent
    permissions: '0755'
    content: |
      #! /bin/sh
      BASEURL="http://metadata.google.internal/computeMetadata/v1/instance/attributes"
      curl -sH "Metadata-Flavor: Google" "${BASEURL}/X-sec" > /etc/jenkins.sec
      chown jenkins /etc/jenkins.sec
      chmod 0640 /etc/jenkins.sec
      JARURL=$(curl  -sH "Metadata-Flavor: Google" "${BASEURL}/X-jar")
      JNLPURL=$(curl -sH "Metadata-Flavor: Google" "${BASEURL}/X-url")
      sudo -u jenkins curl -sL "${JARURL}" > /home/jenkins/agent.jar
      cat > /etc/systemd/system/jenkinsagent.service << EOF
      [Unit]
      Description=Start Jenkins JNLP agent
      Wants=network-online.target
      After=network-online.target
      [Service]
      Type=simple
      User=jenkins
      WorkingDirectory=~
      ExecStart=/usr/bin/java -jar agent.jar -jnlpUrl "${JNLPURL}" -secret @/etc/jenkins.sec
      Restart=on-failure
      RestartSec=1min
      [Install]
      WantedBy=multi-user.target
      EOF
      systemctl enable jenkinsagent.service
      systemctl start jenkinsagent.service
runcmd:
  - '/usr/local/sbin/start-jnlpagent'
```

### Ubuntu on DigitalOcean

The following yaml configuration can be used with cloud-init in an Ubuntu VM running on **DigitalOcean**. It
should be saved in jenkins as custom config file **JClouds user data (yaml)** and then used in the advanced section of the
jclouds template:
```yaml
#cloud-config
users:
  - name: jenkins
    gecos: Jenkins Agent
    shell: /bin/bash
packages:
  - jq
  - openjdk-8-jdk-headless
write_files:
  - path: /usr/local/sbin/start-jnlpagent
    permissions: '0755'
    content: |
      #! /bin/sh
      HOST=$(curl -sL http://169.254.169.254/metadata/v1/hostname)
      AUTH=$(echo -n ${JNLP_NONCE}${HOST} | openssl dgst -sha256 -binary | base64)
      URL="${JENKINS_ROOTURL}jclouds-jnlp-provision/"
      JSON=$(curl -sL --data-urlencode "auth=${AUTH}" --data-urlencode "hostname=${HOST}" "${URL}")
      echo "${JSON}" | jq -r '.["X-sec"]' > /etc/jenkins.sec
      chown jenkins /etc/jenkins.sec
      chmod 0640 /etc/jenkins.sec
      JARURL=$(echo "${JSON}" | jq -r '.["X-jar"]')
      JNLPURL=$(echo "${JSON}" | jq -r '.["X-url"]')
      sudo -u jenkins curl -sL "${JARURL}" > /home/jenkins/agent.jar
      cat > /etc/systemd/system/jenkinsagent.service << EOF
      [Unit]
      Description=Start Jenkins JNLP agent
      Wants=network-online.target
      After=network-online.target
      [Service]
      Type=simple
      User=jenkins
      WorkingDirectory=~
      ExecStart=/usr/bin/java -jar agent.jar -jnlpUrl "${JNLPURL}" -secret @/etc/jenkins.sec
      Restart=on-failure
      RestartSec=1min
      [Install]
      WantedBy=multi-user.target
      EOF
      systemctl enable jenkinsagent.service
      systemctl start jenkinsagent.service
runcmd:
  - '/usr/local/sbin/start-jnlpagent'
```

### Windows on OpenStack

#### Prerequisites:

- A User jenkins must exist or be provisioned.
- Java has to be installed and in PATH
- [WinSW](https://github.com/winsw/winsw/releases) has to be installed as `C:\Users\jenkins\jenkins-agent.exe`.
- The following XML configuration template for WinSW has to be provided as `C:\Users\jenkins\jenkins-agent.xml.tmpl`. The service password has to be hardcoded. All other placeholders will be replaced by the powershell script:
```xml
<configuration>
    <id>jenkins</id>
    <name>Jenkins JNLP agent</name>
    <description>This service runs an agent for Jenkins automation server.</description>
    <executable>java.exe</executable>
    <arguments>-Xrs -Xmx256m -jar "%BASE%\agent.jar" -noCertificateCheck -jnlpUrl "_JNLPURL_" -secret "_SECRET_"</arguments>
    <logmode>rotate</logmode>
    <startmode>automatic</startmode>
    <onfailure action="restart" delay="10 sec"/>
    <download from="_JARURL_" to="%BASE%\agent.jar" />
    <extensions>
        <!-- This is a sample configuration for the RunawayProcessKiller extension. -->
        <extension enabled="true" 
               className="winsw.Plugins.RunawayProcessKiller.RunawayProcessKillerExtension"
               id="killOnStartup">
            <pidfile>%BASE%\jenkins_agent.pid</pidfile>
            <stopTimeout>5000</stopTimeout>
            <stopParentFirst>false</stopParentFirst>
        </extension>
    </extensions>
    <serviceaccount>
       <domain>_CNAME_</domain>
       <user>jenkins</user>
       <password><![CDATA[MySecretServicePassword]]></password>
       <allowservicelogon>true</allowservicelogon>
    </serviceaccount>
</configuration>
```

The following powershell-script then can be used with [cloudbase-init](https://github.com/cloudbase/cloudbase-init) in a Windows VM running on **OpenStack** It
should be saved in jenkins as custom config file **JClouds user data (shell script)** and then used in the advanced section of the
jclouds template:
```powershell
#ps1_sysnative
$ErrorActionPreference = 'Stop'
try {
    $metaUrl = 'http://169.254.169.254/openstack/latest/meta_data.json'
    $meta = Invoke-WebRequest -UseBasicParsing -URI $metaURL | ConvertFrom-Json
    $svpath = 'C:\Users\jenkins\jenkins-agent'
    $xml = Get-Content "$($svpath).xml.tmpl"
    $cname = $env:COMPUTERNAME
    New-Item -type file -path "$($svpath).xml" -force | Out-Null
    Set-Content "$($svpath).xml" $($xml `
        -replace '_JARURL_', $meta.meta.'X-jar' `
        -replace '_JNLPURL_', $meta.meta.'X-url' `
        -replace '_SECRET_', $meta.meta.'X-sec' `
        -replace '_CNAME_', $cname)
    & "$($svpath).exe" 'install'
    $x = $lastexitcode
    if ($x -ne 0) {
        Write-Error "$($svpath).exe install returned $($x)"
    }
    & "$($svpath).exe" 'restart'
    $x = $lastexitcode
    if ($x -ne 0) {
        Write-Error "$($svpath).exe restart returned $($x)"
    }
    exit 0
} catch {
    Write-Error $_.Exception
    if ($null -ne $lastexitcode -and 0 -ne $lastexitcode) {
        exit $lastexitcode
    }
    exit 99
}
exit $lastexitcode
```
