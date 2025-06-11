# CLI commands for the jclouds-plugin

Note: If you don't know about jenkins CLI, read [here][1]  first.

The following CLI commands are provided by the jclouds-plugin:

- [`jclouds-templates`](#user-content-jclouds-templates) Lists all agent templates in all cloud profiles.(#jclouds-templates)
- [`jclouds-provision`](#user-content-jclouds-provision) Provisions an agent node using a template.
- [`jclouds-expire`](#user-content-jclouds-expire) Sets the retention time for a running jclouds agent to zero.
- [`jclouds-copy-template`](#user-content-jclouds-copy-template) Duplicates an existing template, saving it under another name.
- [`jclouds-get-template`](#user-content-jclouds-get-template) Exports a template as XML to stdout.
- [`jclouds-create-template`](#user-content-jclouds-create-template) Creates a new template from XML, supplied at stdin.
- [`jclouds-update-template`](#user-content-jclouds-update-template) Updates an existing JClouds template by reading XML from stdin.
- [`jclouds-copy-cloud`](#user-content-jclouds-copy-cloud) Duplicates an existing cloud profile, saving it under another name.
- [`jclouds-get-cloud`](#user-content-jclouds-get-cloud) Exports a cloud profile as XML to stdout.
- [`jclouds-create-cloud`](#user-content-jclouds-create-cloud) Creates a new cloud profile from XML, supplied at stdin.
- [`jclouds-update-cloud`](#user-content-jclouds-update-cloud) Updates an existing JClouds profile by reading XML from stdin.
- [`jclouds-get-userdata`](#user-content-jclouds-get-userdata) Exports all jclouds userdata as XML to stdout.
- [`jclouds-create-userdata`](#user-content-jclouds-create-userdata) Creates new jcloud userdata reading XML from stdin.

[1]: https://www.jenkins.io/doc/book/managing/cli/

## Command details
<a name="jclouds-templates"></a>
### jclouds-templates
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-templates [-l (--labelmatch) EXPR]
List all JClouds templates.
 -l (--labelmatch) EXPR : List templates that satisfy the specified label
                          expression.
```

With this command you can list all configured JClouds templates on the controller.
A typical output is shown here:

```
Profile      Name                Label                                             Description
==============================================================================================
graustack    bh-stretch-64       linux debian stretch amd64 x86_64 debian64        Buildhost based on Debian Stretch 64bit
graustack    bh-sles12-64        sles sles12                                       Buildhost based on SLES12 64bit
graustack    bh-centos6-64       linux centos6 amd64 x86_64                        Buildhost based on CentOS-6 64bit
graustack    bh-centos7-64       linux centos7 amd64 x86_64                        Buildhost based on CentOS-7 64bit
graustack    bh-rhel6-64         linux rhel6 amd64 x86_64                          Buildhost based on RHEL-6 64bit
graustack    bh-rhel7-64         linux rhel7 amd64 x86_64                          Buildhost based on RHEL-7 64bit
graustack    bh-w2012r2          w2012r2                                           Buildhost, based on Windows Server 2012.R2
graustack    bh-w2016            w2016                                             Buildhost, based on Windows Server 2016
graustack    bh-w2019            w2019                                             Buildhost, based on Windows Server 2019
graustack    bh-w2019-sxs        w2019 w2019.sx secureboot                         Buildhost, based on Windows Server 2019 single-executor
graustack    w2019-jnlp          w2019 w2019.jnlp secureboot                       Buildhost, based on Windows Server 2019 single-executor jnlptest
graustack    bh-w8r1                                                               Buildhost based on Windows 8.1.
graustack    th-gam-rhel7-none   gam-rhel7-none                                    gam rhel7 test host, based on weekly snapshot from devel repo
graustack    th-gam-sles12-none  gam-sles12-none                                   gam sles12 test host, based on weekly snapshot from devel repo
graustack    th-gam-stretch-none gam-stretch-none                                  gam stretch test host, based on weekly snapshot from devel repo
graustack    th-gam-w2012r2-none th-gam-w2012r2                                    gam w2012r2 test host, based on weekly snapshot from devel repo
graustack    th-gam-w2016-none   th-gam-w2016                                      gam w2016 test host, based on weekly snapshot from devel repo
graustack    th-gam-w2019-none   th-gam-w2019                                      gam w2019 test host, based on weekly snapshot from devel repo
graustack    fe-th-w2012r2       th-gam-w2012r2                                    Test host, based on Windows Server 2012 R2
graustack    bh-ub2004-64-sx     linux ubuntu 20.04 amd64 x86_64 ubuntu64 20.04.sx Build host based on Ubuntu Server 20.04 LTS (Focal Fossa) x86_64 single-executor
graustack    sxts-316            sxts-3.16                                         XTS 3.16 server (SSL variant) based on Ubuntu Server 22.04 LTS x86_64
google       gub1804             gub1804
google       bh-stretch-64                                                         Dummy template for testin ambiguous template name
DigitalOcean dub2204             dub2204
DigitalOcean dub2004jnlp         dub2004jnlp
```
By specifying a [`label expression`](https://www.jenkins.io/doc/pipeline/steps/workflow-durable-task-step/#node-allocate-node), you can find out, which templates would satisfy that expression. Of course the label expression usually should be quoted, so that your shell does not try to interpret it.
<a name="jclouds-provision"></a>
### jclouds-provision
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-update-cloud NAME [-d (--delete-templates)] [-k (--keep-templates)] [-v (--verbose)]
Updates an existing JClouds profile by reading XML from stdin.
 NAME                    : Name of the profile to update.
 -d (--delete-templates) : Delete templates of target profile. (default: false)
 -k (--keep-templates)   : Keep templates of target profile. (default: false)
 -v (--verbose)          : Be verbose when validating references to
                           credentials. (default: false)
```
With this command, you can create new VM (agent node) in the cloud. The profile parameter is require only, if you have multiple profiles and each of them contains a template with the name you specified. The command blocks, until the cloud has created the VM instance or an error occured. The name of the agent node is taken from the name of the template with a uniqe suffix appended. This suffix depends on which cloud
is used.
A typical output is shown here:
```
Provisioned node sxts-316-bf2 with Address(es) 192.168.252.158
```
The --format parameter specifies the format of that output. With ```--format JSON``` you would get this instead:
```
{ "name": "sxts-316-bf2", "addr": ["192.168.252.158"] }
```
and with ```--format PROPERTIES```, it would be:
```
name=sxts-316-bf2
addr=192.168.252.158
```
<a name="jclouds-expire"></a>
### jclouds-expire
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-expire NODENAME
Sets a nodes retention time to 0 in order to get it deleted at next cleanup.
 NODENAME : The name of the node to be expired.
```
This is basically a more gentle way of the core commands `disconnect-node` followed by `delete-node`.  It sets the retention time of that agent node to zero. Therefore, if the agent node is still running some job, the actual cleanup of the node is postponed until that node is idle.
<a name="jclouds-copy-template"></a>
### jclouds-copy-template
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-copy-template FROM-TEMPLATE TO-TEMPLATE [PROFILE]
Duplicates an existing node template to a different name in the same profile.
 FROM-TEMPLATE : Name of the existing template to copy
 TO-TEMPLATE   : Name of the new template to create
 PROFILE       : Name of jclouds profile. Required, if FROM-TEMPLATE is
                 ambiguous
```
<a name="jclouds-get-template"></a>
### jclouds-get-template
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-get-template TEMPLATE [PROFILE] [-r (--replace)]
Exports a template as XML to stdout.
 TEMPLATE       : Name of template to export.
 PROFILE        : Name of jclouds profile to use. Required, if TEMPLATE is
                  ambiguous.
 -r (--replace) : Read replacements as XML from stdin. (default: false)
```
<a name="jclouds-get-template-remarks"></a>
#### Remarks
When exporting a template in order to import it on a different jenkins controller, the problem of validating and probably mapping the various references to credentials and user data (managed config files) arises. Of course credentials-ids must refer to tha same credentials on the destination controller. Therefore, a hash is included in the output of every credentials-id and config file id. These hashes ar calculated over the actual content (e.g. username/password, private key or config file content and name) and therefore can be validated. So even if, for example, a matching config-file id exists but the content is different, import on the destination controller will be rejected. The  [`jclouds-create-userdata`](#user-content-jclouds-create-userdata) command can create a replacement XML data, when used with the `--merge` option. Then, using this replacement-data, the exported XML can be modified in order to match the merged user data on the destination controller. Unfortunately, for credentials-ids there is only a partial export/import without the actual secrets. So while validating using the hashes works, The actual transfer of credentials-ids needs to be done sparately.
<a name="jclouds-create-template"></a>
### jclouds-create-template
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-create-template NAME [PROFILE] [-v (--verbose)]
Creates a new node template by reading XML from stdin.
 NAME           : Name of the new template to create.
 PROFILE        : Name of destination jclouds profile. Required, if multiple
                  profiles exist.
 -v (--verbose) : Be verbose when validating references to credentials and
                  config files. (default: false)
```
This is the import functionality for templates. It reads the XML, which was generated by the above command and creates a new template.
<a name="jclouds-update-template"></a>
### jclouds-update-template
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-update-template TEMPLATE [PROFILE] [-v (--verbose)]
Updates an existing JClouds template by reading XML from stdin.
 TEMPLATE       : Name of the existing template to update.
 PROFILE        : Name of jclouds profile to use. Reqired, if TEMPLATE is
                  ambiguous.
 -v (--verbose) : Be verbose when validating references to credentials and
                  config files. (default: false)
```
This updates an existing template from the (possibly modified) XML, created by reading the XML, generated by [`jclouds-get-template`](#user-content-jclouds-get-template).
<a name="jclouds-copy-cloud"></a>
### jclouds-copy-cloud
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-copy-cloud FROM-CLOUD TO-CLOUD
Duplicates an existing JClouds cloud profile to a different name.
 FROM-CLOUD : Name of the existing jclouds profile to copy.
 TO-CLOUD   : Name of the new jclouds profile to create.
```
<a name="jclouds-get-cloud"></a>
### jclouds-get-cloud
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-get-cloud PROFILE [-f (--full)] [-r (--replace)]
Exports a cloud profile as XML to stdout.
 PROFILE        : Name of jclouds profile to use.
 -f (--full)    : Include all templates of this cloud in the export. (default:
                  false)
 -r (--replace) : Read replacements as XML from stdin. (default: false)
```
Regarding the `--replace` option, the same [remarks](#user-content-jclouds-get-template-remarks) apply like with templates.
<a name="jclouds-create-cloud"></a>
### jclouds-create-cloud
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-create-cloud NAME [-v (--verbose)]
Creates a new cloud profile by reading XML from stdin.
 NAME           : Name of the new jclouds profile to create.
 -v (--verbose) : Be verbose when validating references to credentials and
                  config files. (default: false)
```
This is the import functionality for cloud profiles. It reads the XML, which was generated by [`jclouds-get-cloud`](#user-content-jclouds-get-cloud) and creates a new cloud.
<a name="jclouds-update-cloud"></a>
### jclouds-update-cloud
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-update-cloud NAME [-d (--delete-templates)] [-k (--keep-templates)] [-v (--verbose)]
Updates an existing JClouds profile by reading XML from stdin.
 NAME                    : Name of the profile to update.
 -d (--delete-templates) : Delete templates of target profile. (default: false)
 -k (--keep-templates)   : Keep templates of target profile. (default: false)
 -v (--verbose)          : Be verbose when validating references to
                           credentials. (default: false)
```
This updates an existing cloud profile from the (possibly modified) XML, which was generated by [`jclouds-get-cloud`](#user-content-jclouds-get-cloud). The options `--delete-templates` and `--keep-templates` handle the special situation, where the imported XML does **NOT** include templates.
<a name="jclouds-get-userdata"></a>
### jclouds-get-userdata
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-get-userdata CREDENTIAL
Exports all jclouds userdata as XML to stdout.
 CREDENTIAL : ID of credential (Must be a RSA SSH credential) to encrypt data.
```
<a name="jclouds-create-userdata"></a>
### jclouds-create-userdata
SYNTAX:
```
java -jar jenkins-cli.jar jclouds-create-userdata [CREDENTIAL] [--merge] [--overwrite]
Creates new jcloud userdata reading XML from stdin.
 CREDENTIAL  : ID of credential (Must be an RSA SSH credential) to encrypt
               data. Default: Taken from input XML.
 --merge     : Generate new Ids for imported userdata files if the id already
               exists and references different user data. (default: false)
 --overwrite : Overwrite existing userdata files. (default: false)
```
When using the `--merge` option, a special replacement XML will be generated on stdout, **IF** conflicts were resolved by generating new file ids. This XML then can be used with [`jclouds-get-template`](#user-content-jclouds-get-template) and/or [`jclouds-get-cloud`](#user-content-jclouds-get-cloud).
