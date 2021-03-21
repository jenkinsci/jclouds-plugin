JClouds Plugin for Jenkins
==========================

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/jclouds-jenkins.svg)](https://plugins.jenkins.io/jclouds-jenkins)
[![Changelog](https://img.shields.io/jenkins/plugin/v/jclouds-jenkins.svg?label=changelog)](https://github.com/jenkinsci/jclouds-plugin/blob/master/CHANGELOG.md#changelog)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/jclouds-jenkins.svg?color=blue)](https://plugins.jenkins.io/jclouds-jenkins)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/jclouds-plugin/master)](https://ci.jenkins.io/job/Plugins/job/jclouds-plugin/job/master/)

## About this plugin

This plugin uses [JClouds](http://jclouds.org/) to
provide agent launching on most of the currently usable Cloud
infrastructures [supported](http://jclouds.apache.org/reference/providers/#compute-providers) by JClouds.

## Adding a new Cloud Provider

-   Goto Jenkins Configuration page by clicking on `Manage Jenkins`→`Manage Nodes and Clouds`→`Configure Clouds` or
    browsing to the URL <http://localhost:8080/configureClouds>
-   Scroll down to Cloud Section
-   Click on the \`Add a new cloud\` pop-up menu button which should
    have an option - \`Cloud (JClouds)\`
-   Click on \`Cloud (JClouds)\`
-   Fill in the configuration options
    -   Profile : the name of the profile e.g, `aws-agent-profile`.
    -   Provider Name: Select from the supported providers.
    -   End Point URL: if your provider API needs an endpoint configuration, add it here, otherwise leave this empty.
    -   Max Number of Instances: The maximum number of instances to run from this cloud at one time.
    -   Retention Time: How long, in minutes, to wait for an agent to
remain idle before disconnecting and terminating it. Defaults to `30`.
    -   Credentials: Depending on your provider, you either need a
username/password pair or a username/privatekey pair. The first
one is covered by the "Username with password" credentials of
jenkins and for the second one, you simply can use the
"SSH Username with private key" credentials which - despite of
what their name suggests - can store **any** kind of PEM-encoded
private key. For  GCE, select the "JClouds Username with key" credential type and
then click the Browse button in order to upload your JSON key from google.  
(In case of openstack-nova keystoneV2, the username is to be specified in the form `tenantName:userName`).  
New since 2.15 is support for OpenStack keystone-V3: Select "OpenStack keystone V3 credentials" and fill
all the fields (The Domain usually is named `Default`)
    -   RSA Private Key/Public Key: Like with the credentials, simply
use a "SSH Username with private key" credential. The public key
is derived automatically from that.
    -   Click on \`Test Connection\` to validate the cloud settings.
-   Add Cloud Instance Template by clicking on the Add button
-   Fill in configuration options:
    -   Name : the name of the instance template e.g.
        `aws-jenkins-agent`
    -   Number of Executors: How many executors each agent created from
        this template should have.
    -   Description: notes/comments for your reference.
    -   Image ID: Use one of the following options:
        -   Image ID to use for this agent template, such as EC2 AMIs.
Note that EC2 AMIs must include the region as well, e.g., `us-east-1/ami-00000`.
If unshure, you can enter a partial string and the hit the "Check Image Id" button. This
searches for matching images and if there are ambiguties, a  message is shown like: "Did you mean
            "....exact-image-id..."?  **or**
        -   OSFamily: Specify the OSFamily - leave empty for default for a cloud provider
        -   OS Version : Specify the OSVersion - leave empty for default for a cloud provider
    -   Hardware ID: Use one of the following options:
        -   Hardware ID on provider for this agent template, such as `t1.micro` on AWS EC2.
            **or**
        -   RAM : in MB
        -   No. of Cores: number of virtual processor cores.
    -   Location ID: Location ID where this agent will be deployed. If none is selected jclouds will automatically choose an available one.
    -   Labels: (space-separated) labels/tags that you can use to attach a build to this agent template.
    -   Init Script: A shell script to be run when the agent is created.
        A rather crude method for provisioning. If supported by your
        provider, you should prefer the use of User-Data and with cloud-init resp. cloudbase-init on Windows agents.
    -   Stop on Terminate: If true, suspend agents rather than terminating them.
-   Click Save to save the configuration changes.
-   Goto Jenkins' home page, click on \`Build Executor Status\` link on
    the sidebar.
-   Verify that you have a button with \`Provision via JClouds - (YOUR
    PROFILE NAME) drop down with the agent template name you configured.
-   Click on the agent and see if your agent launched successfully
    (please wait until the operation completes).

### Executing build on the agent

-   To run your build on the newly configured agent, just
    enable the \`Restrict where this project can be run\` option in the
    build configuration page.
-   Enter the label which you choose for the instance template in the
    \`Label Expression\` text field. This should auto-complete labels
    for you.
-   Click save to save the configuration options.
-   Schedule the build to check whether the build is executed on the
    selected agent template.
-   If your cloud provider is charging by the minute (GCE for example),
    you can enable the checkbox "JClouds Single-use-agent" which
    destroys the provisioned  
    VM after the build job has finished (with a small delay of \~ 1min).

## Adding a Blobstore Profile for storing build artifacts

The plugin also provides a way to store your build artifacts on JClouds
supported cloud storage providers. You can configure multiple  
blobstore profiles and configure the build to copy different
files/artifacts to the specified container. Here's how you configure the
same.

-   Goto Jenkins Configuration Page
-   Click Ad under the section \`JClouds Cloud Storage Settings\`
-   Provide the configuration Options:
    -   Profile Name: name of the profile e.g. aws-storage
    -   Provider Name: Select one of the supported providers.
    -   Credentials: Just like the cloud credentials.
-   You can add multiple providers by clicking on Add.
-   Click Save on the bottom of the page, to save the settings.

### Publishing artifacts after a build

After you configure a cloudstorage provider, you can enable the
publishing file by enabling it under \`Post-build Actions\` in the build
job configuration page.

-   Click on the checkbox \`Publish artifacts to JClouds Clouds
    Storage\`
-   You should now see a dropdown with configured storage profiles.
    Select the one you want to use for this build.
-   Click on Add button next to \`Files to upload\`.
-   Add the sourced file path (relative to workspace)
-   Add the destination container name.
-   Repeat to add more files if you want to copy.
-   Click save.

When the build is complete and successful, the configured files will be
published to the configured blobstore.

## Merged cloud-init YAML definitions

When using cloud-init you can provide multiple config data snippets. If
you select YAML snippets, those get merged. E.g:

If snippet 1 contains:

    packages:
      - screen
      - openjdk-8-jdk-headless

and snippet 2 contains:

    packages:
      - gcc

Then the resulting YAML on the cloud-init (server) side becomes:

    packages:
      - gcc
      - screen
      - openjdk-8-jdk-headless

## Using the phone-home feature

When provisioning agents, there might be too much work on an agent for it
to get ready (listening on port 22) in time for jenkins.  
Therefore, the plugin provides a webhook, which is designed to be
invoked by a http POST request using
[cloud-init](http://cloudinit.readthedocs.io/en/latest/topics/examples.html#call-a-url-when-finished)
within the agent when everything is ready to use. When enabled, the
usual agent connection setup is delayed until the http POST is received.

-   The corresponding cloud-init configuration looks like this:

        #cloud-config
        phone_home:
          url: http://your.jenkins.url/jclouds-phonehome/
          tries: 3

-   You can put this into the User data field in the agent template for
example. In the advanced template configuration you now can check the Checkbox "Wait for agent to phone home" and
specify a timeout value (in minutes). When provisioning an agent, jenkins now waits for the agent to
invoke the webhook before launching the ssh remote connection. 

## Using JNLP

Information about using JNLP instead of ssh (primarily for Windows-based agents, but works for Unix agents too)
can be found [here](https://github.com/jenkinsci/jclouds-plugin/blob/master/JNLPPROVISIONING.md)
