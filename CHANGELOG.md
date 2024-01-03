## Changelog

### Version 2.34 (released Jan 3, 2024)
- Added workaround for incompatibility of jclouds with newer GSON libs. Fixes ([JENKINS-72475](https://issues.jenkins.io/browse/JENKINS-72475))
### Version 2.33 (released Nov 20, 2023)
- Removed dependency on Prototype.js
### Version 2.32 (released Nov 5, 2023)
- Migrated to jacarta-mail (thanks to Basil Crow)
- Refresh plugin for June 2023 (thanks to Basil Crow)
- Added post build action for taking agent offline, if build is unsuccessful.
### Version 2.31 (released Jan 30, 2023)
- Use `Cloud#getUrl` in /provision URL (thanks to Yaroslav Afenkin)
- Add optional indexName parameter to JCloudsBuildWrapper
### Version 2.30 (released Jan 26, 2023)
- Improve JCloudsBuildWrapper:
  - Add an optional parameter to specify an environment variable that receives the
    IP-address(es) of supplemental nodes.
  - Add an optional parameter to specify existing environment variables that get published
    to the supplemental nodes as properties (OpenStack and GCE only).
  - Add property `JCLOUDS_SUPPLEMENTAL_INDEX` to supplemental nodes in order to distinguish
    multiple supplemental nodes.
- Changes to upcoming styling of file upload in JCloudsUserWithKey (thanks to Tim Jacomb)
### Version 2.29 (released Jun 26, 2022)
- Optimize operation: No need for cleanup, if zero supplemental nodes were launched.
### Version 2.28 (released Jun 24, 2022)
- Improved usability:
  Print nice message instead of stacktrace if build was aborted while
  waiting for supplemental nodes to phone home.
### Version 2.27 (released Jun 24, 2022)
- Fixed several serialization problems with JCloudsBuildWrapper
- JCloudsBuildWrapper did not wait for supplemental nodes
- Supplemental nodes were not terminated, if build was aborted
- Use built-in Base64 encoder instead of JAXB (thanks to Basil Crow)
### Version 2.26 (released Apr 17, 2022)
- Removed shaded guava and guice in jclouds, because jenkins has updated guava/guice versions now (thanks to Basil Crow)
- Bump up jclouds version to 2.5.0. See [release notes](https://jclouds.apache.org/releasenotes/2.5.0/)
- Removed jclouds labs providers oneandone and profitbricks-rest, because they were removed in jclouds 2.5.0
### Version 2.25 (released Sep 22, 2021)
- Stop using deprecated `Util#join` (thanks to Basil Crow)
- Bump up jclouds version to 2.4.0. See [release notes](https://jclouds.apache.org/releasenotes/2.4.0/)
- Make jcloudsOneOffAgent available in pipeline snippet generator
- Make JCloudsBuildWrapper available as withJclouds in snippet generator
### Version 2.24 (released Aug 15, 2021)
- Prepare for core guava update. Fixes ([JENKINS-66318](https://issues.jenkins-ci.org/browse/JENKINS-66318))

### Version 2.23 (released Mar 22, 2021)
- Java11 compatibility
- Bump up jclouds to v2.3.0
- Moved jclouds-shaded into separate external project
- Added workaround for recent javax.mail versions
- Fixed misc. deprecation warnings

### Version 2.22 (released Mar 17, 2021)
- Replace table markup by div in jenkins >= 2.264 ([JENKINS-64537](https://issues.jenkins-ci.org/browse/JENKINS-64537))
- Bump up jclouds to v2.2.1

### Version 2.21 (released Mar 7, 2021)
- Fixed ([JENKINS-44613](https://issues.jenkins-ci.org/browse/JENKINS-44613))
- Enhanced error handling: Cloud nodes that fail to connect are now deleted. The old behavior can be restored by setting errorRetentionTime to -1

### Version 2.20 (released May 8, 2020)
- Fixed enabling/disabling of public IP assignment on GCE ([JENKINS-62199](https://issues.jenkins-ci.org/browse/JENKINS-62199))

### Version 2.19 (released May 8, 2020)
- broken. Do not use

### Version 2.18 (released April 25, 2020)
- Fixed a security issue: jclouds-expire cli command did not check permissions.
- Fixed race condition with OneOff slaves
- Fixed cleanup of OneOff slaves ([JENKINS-61788](https://issues.jenkins-ci.org/browse/JENKINS-61788))
- New feature: Provisioning of JNLP parameters (urls and credentials).

### Version 2.17 (released December 16, 2019)
- Fixed doc migration from wiki to github README.md

### Version 2.16 (released December 16, 2019)

-   Fixed subnet handling of aws-ec2 provider. Thanks to [Basil Peace](https://github.com/grv87). (Fixes [JENKINS-47301](https://issues.jenkins-ci.org/browse/JENKINS-47301))
-   Added support for GCP preemptible instances. Thanks to [Mathieu Tortuyaux](https://github.com/tormath1) (Fixes [JENKINS-44601](https://issues.jenkins-ci.org/browse/JENKINS-44601))
-   Added test case for cloud storage providers. Thanks to [Daniel Kutik](https://github.com/danielkutik)
-   Convert JCloudsOneOffSlave to a SimpleBuildWrapper so that it may be used from Pipeline scripts. Thanks to [Carlos Tadeu Panato Junior](https://github.com/cpanato)
-   Switch labels from entry to checkbox. Thanks to [Josh Soref](https://github.com/jsoref) (Fixes [JENKINS-55787](https://issues.jenkins-ci.org/browse/JENKINS-55787))
-   Added jclouds-expire cli command
-   Added support for new SSHLauncher API in ssh-slaves plugin > v1.29.4.
-   Upgraded to [jclouds-2.2.0](https://jclouds.apache.org/releasenotes/2.2.0/) (Fixes [JENKINS-60499](https://issues.jenkins-ci.org/browse/JENKINS-60499))

### Version 2.15 (released August 6, 2019)

-   Fixed SECURITY-1482 / CVE-2019-10368 (CSRF) and CVE-2019-10369
    (permission check).
    \[[Advisory](https://jenkins.io/security/advisory/2019-08-07/)\]
-   Suppress binary userData in logs
    ([JENKINS-41989](https://issues.jenkins-ci.org/browse/JENKINS-41989))
-   Improve help for userData
-   Implement workaround for broken cloudbase-init mime handling
-   Added support for cloud statistics plugin (now a mandatory
    dependency)
-   Added packet provider (Thanks to Ignasi Barrera)
-   Added profitbricks provider (thanks to Ali Bazlamit)
-   Fixed GUI problem
    ([JENKINS-48986](https://issues.jenkins-ci.org/browse/JENKINS-48986))
    (thanks to Tomasz Wojtun)
-   Upgrade to jclouds-2.1.0, (Fixes
    [JENKINS-42005](https://issues.jenkins-ci.org/browse/JENKINS-42005))
-   Added support for OpenStack keystone-V3 authentication

### Version 2.14 (released February 14, 2017)

-   Upgraded to config-file-provider-2.15.6
    ([JENKINS-41078](https://issues.jenkins-ci.org/browse/JENKINS-41078))

### Version 2.13 (released February 13, 2017)

-   Fixed class loading problem with bouncycastle resulting in
    InvalidKeySpecException: key spec not recognised.
    ([JENKINS-41186](https://issues.jenkins-ci.org/browse/JENKINS-41186))

### Version 2.12 (released December 16, 2016)

-   New template config option "Preferred Address" solves problem with
    multi-homed slaves.
    ([JENKINS-40342](https://issues.jenkins-ci.org/browse/JENKINS-40342))
-   Use gzip compression of userData on supporting cloud providers;
    (currently: aws-ec2, openstack-nova and openstack-nova-ec2)

### Version 2.11 (released December 5, 2016)

-   Upgrade to jclouds-2.0 (Among many improvents, this adds official
    support for GCE (Google) and DigitalOcean2)
-   Do not send null userData
    ([JENKINS-21108](https://issues.jenkins-ci.org/browse/JENKINS-21108))
-   Generate artificial suffix for Instances on EC2
    ([JENKINS-19935](https://issues.jenkins-ci.org/browse/JENKINS-19935))
-   Added missing license
    ([JENKINS-40028](https://issues.jenkins-ci.org/browse/JENKINS-40028))
-   Now shows IP address on node details page
    ([JENKINS-14398](https://issues.jenkins-ci.org/browse/JENKINS-14398))
-   Now uses the
    [config-file-provider-plugin](https://wiki.jenkins-ci.org/display/JENKINS/Config+File+Provider+Plugin)
    for initScripts and userData
-   Allows for multiple cloud-config snippets in userData
-   Merged OpenStack's assignFloatingIP setting into assignPublicIp
    (same semantic)
-   Improved automatic migration when upgrading the plugin. Now,
    upgrades from v2.8.1-1  
    should go seamlessly.

### Version 2.10 (released November 18, 2016)

-   Set Content-Length in blobstore requests
    ([JENKINS-23049](https://issues.jenkins-ci.org/browse/JENKINS-23049))
-   Generally improved blobstore error handling.
-   Blobstore publisher now can be further configured
    ([JENKINS-22178](https://issues.jenkins-ci.org/browse/JENKINS-22178))
-   Added missing dependency on ssh-credential plugin
-   Added config drive flag
    ([JENKINS-23454](https://issues.jenkins-ci.org/browse/JENKINS-23454))
-   Improved deletion of expired slave nodes
    ([JENKINS-27471](https://issues.jenkins-ci.org/browse/JENKINS-27471)
    and
    [JENKINS-28403](https://issues.jenkins-ci.org/browse/JENKINS-28403))
-   Supplmental instances now properly use phone home feature
-   Properly destroy stale instances after a forced reset/shutdown of
    jenkins

### Version 2.9 (released November 9, 2016)

-   All credential related settings now use the credential-plugin
-   New webhook for synchronizing slave availability (aka. phone-home
    for slaves)
-   Added CLI commands (jclouds-templates, jclouds-provision)
-   Fixed several potential XSS issues
-   Upgrade to jclouds-1.9.2
-   Safer cleanup of expired slaves
-   Added many missing help texts
-   Fixed classloader isolation problem with jenkins 2.29
    ([JENKINS-39505](https://issues.jenkins-ci.org/browse/JENKINS-39505))
-   Upgrade to ne credentials API
    ([JENKINS-35560](https://issues.jenkins-ci.org/browse/JENKINS-35560))
-   Fixed reconnection to running slaves after jenkins restart
    ([JENKINS-30556](https://issues.jenkins-ci.org/browse/JENKINS-30556))
-   Added workaround for image cache
    ([JENKINS-28815](https://issues.jenkins-ci.org/browse/JENKINS-28815))
-   Fixed NPE when setting retention time
    ([JENKINS-29136](https://issues.jenkins-ci.org/browse/JENKINS-29136))
-   Fixed floating ip bug on openstack
    ([JENKINS-26083](https://issues.jenkins-ci.org/browse/JENKINS-26083))
-   Fixed scheduling of matrix jobs and added ability to configure node
    usage mode
    ([JENKINS-25865](https://issues.jenkins-ci.org/browse/JENKINS-25865))
-   Fixed handling of OpenStack security groups
    ([JENKINS-22587](https://issues.jenkins-ci.org/browse/JENKINS-22587))
-   Added validation of various number fields

### Version 2.8.1-1 (released December 2, 2015)

-   Add flag for copying and executing `slave.jar` on Windows images

### Version 2.5.1 (released March 5, 2014)

-   Switch back to Java 1.6
-   Jclouds version updated to 1.7.1
-   Allow shading guava and jclouds
-   Added the DigitalOcean provider
-   Added an option to let users install private key in the slave
-   Added option to specify networks requirements for any provider

### Version 2.5 (released February 5, 2014)

-   Added OpenStack zones parameter to restrict spawning cloud slaves to
    them
-   Added capability to run slaves by specifying image name regexp
    instead of image ID
-   Bumped Java version to 1.7
-   Credential field is now password type
-   JCloudSlave now inherited from AbstractCloudSlave

### Version 2.4 (released August 27, 2013)

-   Bump up jclouds to 1.6.2-incubating.
-   Allow the user to not allocate a public IP address on CloudStack.
    ([JENKINS-18461](https://issues.jenkins-ci.org/browse/JENKINS-18461))
-   Various OpenStack fixes
-   Add support for user data and persisting credentials.
-   Various blobstore improvements
-   Don't print auth stack trace
    ([JENKINS-16632](https://issues.jenkins-ci.org/browse/JENKINS-16632))
-   Add forcible synchronized serial delay option
    ([JENKINS-15970](https://issues.jenkins-ci.org/browse/JENKINS-15970))

### Version 2.3 (released August 31, 2012)

-   Bump up jclouds to 1.5.0-beta.11.
-   Add the ability to override retention time for an individual
    template.
-   Now able to specify "Launch Instance" template as a string with
    parameter replacement enabled.

### Version 2.2.2 (released July 18, 2012)

-   Bump up jclouds to 1.5.0-beta.7.
-   Use Remote FS Root for Jenkins user home.
    ([JENKINS-14396](https://issues.jenkins-ci.org/browse/JENKINS-14396))

### Version 2.2.1 (released July 11, 2012)

-   Bump up jclouds to 1.5.0-beta.6.

### Version 2.2 (released June 22, 2012)

-   Based on JClouds 1.5.0-beta.4, adding support for a bunch of new
    cloud providers, including CloudStack 3.x, Greenqloud, and more.
-   Added Single Use Slave option - if enabled, the slave will only be
    used for one build and then deleted.
-   Dramatically improved performance of slave deletion, which should
    also help avoid lockups of Jenkins itself.

### Version 2.1.1 (released May 24, 2012)

-   Fixed a showstopper bug that actually broke everything UI-related.
    whoops!

### Version 2.1.0 (released May 23, 2012)

-   Bumped to JClouds 1.5.0-beta.1.
-   Initial support for spinning up non-slave instances as part of a
    build.
-   Don't create a Jenkins user on the slave if one already exists.
-   Add a configurable timeout for slave init.
-   Improved logging.

### Version 2.0.4 (released May 1, 2012)

-   Hardware ID wasn't being used.
-   Got rid of excess persisted NodeMetadata from slaves.
-   Cleaned up slave template UI.
-   Added option for infinite retention with retention time of -1.

### Version 2.0.3 (released Apr 26, 2012)

-   Modified package manifest to mask older guava jars from plugin's
    classloader, so that the plugin will work consistently on pre-1.463
    Jenkins releases.

### Version 2.0.2 (released Apr 25, 2012)

-   First real release.
-   Support for multiple clouds on a single Jenkins instance.
-   Auto-provisioning functional.
