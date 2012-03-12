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