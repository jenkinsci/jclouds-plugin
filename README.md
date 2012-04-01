# JClouds Plugin for Jenkins

JClouds Jenkins plugin provides option to launch jenkins slaves on any Cloud provider supported by JClouds (http://www.jclouds.org/documentation/reference/supported-providers). 

### Configuration Options

You can add a new Cloud in the Jenkins configuration. The plugin requires the cloud provider type (as per JClouds Compute API provider list), access id and secret key. You can generate a keypair that will be used to connect and configure the slave.

The list of providers is an auto-complete field. There's an option to test the connection settings, which will validate your credentials. A new node can be launched via Jenkin's computer list (build executors) screen. 

We are working on more configuration options to configure auto-provisioning  and limits etc. 

## Building and Running

You can build and test this plugin using by cloning this repository and 

`mvn clean install hpi:run`

The above command will start jenkins with JClouds plugin pre-configured.

## Adding a new Cloud Provider

* Goto Jenkins Configuration page by clicking on Manage menu or browsing to the URL http://localhost:8080/configure
* Scroll down to Cloud Section
* Click on the `Add a new cloud` pop-up menu button which should have an option - `Cloud (JClouds)`
* Click on `Cloud (JClouds)`
* Fill in the configuration options
  - Profile : the name of the profile e.g, aws-slave-profile
  - Provider Name: type first two characters and you'll get an auto-completed provider name (e.g. aws-ec2 or hpclou-compute)
  - End Point URL: if your provider API needs an endpoint configuration, add it here, otherwise leave it empty.
  - Identity : your accessId
  - Credential: your secret key
  - RSA Private Key/Public Key: If you have a keypair, then just copy paste the public and private key parts, otherwise click on `Generate Key Pair` button.
  - Click on `Test Connection` to validate the cloud settings.
  
* Add Cloud Instance Template by clicking on the Add button
* Fill in configuration options:
  - Name : the name of the instance template e.g. aws-jenkins-slave
  - Description: notes/comments for your reference.
  - OSFamily: Specify the OSFamily - leave empty for default for a cloud provider
  - OS Version : Specify the OSVersion - leave empty for default for a cloud provider
  - RAM : in MB
  - No. of Cores: number of virtual processor cores.
  - Labels: (space-separated) labels/tags that you can use to attach a build to this slave template

* Click Save to sve the configuration changes.
* Goto Jenkins' home page, click on `Build Executor Status` link on the sidebar.
* Verify that you have a button with `Provision via JClouds - {YOUR PROFILE NAME} drop down with the slave template name you configured.
* Click on the slave and see if your slave launched succesfully (please wait until the operation completes).



                                                                                