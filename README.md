# nuxeo-s3-utils


This add-on for [Nuxeo](http://www.nuxeo.com) contains utilities for AWS S3: Operations to upload or download a file, utilities to build a temporary signed URL to S3, and a helper SEAM Bean.

## Set Up: Configuration
### Principles and Authentication
The plugin creates a `S3Handler` tool, that is in charge of performing the actions (download, upload, ...). It connects to S3 using your credentials, a region and a bucket.

The plugin uses Nuxeo AWS Credential code to handle authentication (the `NuxeoAWSCredentialsProvider` class), which means you must either:

* Use nuxeo configuration parameters/XML extension point to set up your AWS credentials (see https://doc.nuxeo.com/nxdoc/amazon-s3-online-storage/)
* Be already authenticated and/or you have setup the expected AWS environment variables.

For example, on way to run unit tests is to: (in a terminal):

* Authenticate to AWS (like, run `aws s3 ls` and authenticate)
* Then, run `mvn install`

I Nuxeo is deployed on an EC2 instance on AWS, it automatically gets the authentication and role from the instance.

In order to allow connecting to several to several buckets (within the same account, as you already are authenticated to this account, the plugin exposes a _service_, allowing you to access different buckets: You contribute as many S3 Handlers as you need, given each of them a unique name.

**IMPORTANT**: Of course, authentication drives permission, you must make sure the account use (or the EC2 instance running) has permission to download, upload, delete, ... in the bucket.

### Contribute the S3Utils Service
Fo each S3 account and bucket you want to access, just add the following contribution to your Nuxeo Studio project (Advanced Settings > XML Extension). Values are explain below.

```
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>HANDLER_NAME</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <region>THE_REGION</region>
    <bucket>THE_BUCKET</bucket>
    <tempSignedUrlDuration>THE_DURATION</tempSignedUrlDuration>
    <useCacheForExistsKey>true or false</useCacheForExistsKey>
  </s3Handler>
</extension>
```
Replace the values with yours:

* `name`: Required. The unique name of your handler. To be used in some operations
* `class`: Required. Do not change this one, keep`org.nuxeo.s3utils.S3HandlerImpl` (unless you write your own handler, see the code)
* `region`: Required.
  * The region (always required, even if buckets are global)
  * If this property is empty, the plugin reads the region from:
    * The `nuxeo.aws.s3utils.region` configiration parameter
    * If empty, reads from Nuxeo AWS configuration
    * If still empty, reads from the `nuxeo.aws.region` confifguration parameter
* `bucket`: Required. The bucket to use for this S3 account.
* `tempSignedUrlDuration`: Optional.
  * The duration, in seconds, of a temporary signed URL.
  * If not passed or negative, a default value of 1200 (2 minutes) applies
* `useCacheForExistsKey`: Optional.
  * pass `true` or `false`. Tell the plugin to use a cache when checking the existence of a key in the S3 bucket, to avoid calling S3 too often.
  * Default value is `false`

### Use `nuxeo.conf`
It may be interesting to read the value from `nuxeo.conf`. This way, you can deploy the same Studio projet in different environments (typically Dev/Test/Prod), each of them using a different set of regions and buckets.

For this purpose:

1. Declare your custom configuration parameters in nuxeo.conf
2. Use them in the XML declaration, using the templating language: `${your.custom.key:=}`

The plugin provides default configuration parameters...

```
<extension target="org.nuxeo.s3utils.service" point="configuration">
	<s3Handler>
		<name>default</name>
		<class>org.nuxeo.s3utils.S3HandlerImpl</class>
		<region>${nuxeo.aws.s3utils.region:=}</region>
		<bucket>${nuxeo.aws.s3utils.bucket:=}</bucket>
		<tempSignedUrlDuration>${nuxeo.aws.s3utils.duration:=}</tempSignedUrlDuration>
		<useCacheForExistsKey>${nuxeo.aws.s3utils.use_cache_for_exists_key:=}</useCacheForExistsKey>
	</s3Handler>
</extension>
```

...so you can use them to set up the `default` handler:

```
# in nuxeo.conf
nuxeo.aws.s3utils.region=eu-west-1
nuxeo.aws.s3utils.bucket=my-test-bucket
nuxeo.aws.s3utils.duration=300
nuxeo.aws.s3utils.use_cache_for_exists_key=false

```

## Set Up: An Example
Using `nuxeo.conf` and XML Extension.

We are going to setup 2 handlers accessing the same S3 account, same region, but two different buckets.
 
1. In `nuxeo.conf`, we add the following:

```
nuxeo.aws.s3utils.region=eu-west-1
mycompany.s3.bucketOne=the-bucket
mycompany.s3.bucketTwo=the-other-bucket
```

2. In our Studio project, we create two XML extension
  * First one (notice: Give whatever name you want to the XML Extension itself) uses the region and "the-bucket"

```
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>S3-Bucket-one</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <region>${nuxeo.aws.s3utils.region:=}</region>
    <bucket>${mycompany.s3.bucketOne:=}</bucket>
    <!-- Let default values for other parameters -->
  </s3Handler>
</extension>
```

 * Second one uses the region, "the-other-bucket", and only one minute of duration for the temporary signed URLs:

```
<!-- Let default values for other parameters -->
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>S3-Bucket-Two</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <region>${nuxeo.aws.s3utils.region:=}</region>
    <bucket>${mycompany.s3.bucketTwo:=}</bucket>
    <tempSignedUrlDuration>60</tempSignedUrlDuration>
  </s3Handler>
</extension>
```

Now, we can use the "S3-Bucket-one" or the "S3-Bucket-Two" handlers.

⚠️ In this example, we don't set the values for the `default` handler ⚠️

* It cannot be used (using it will fail, no bucket is defined in `nuxeo.aws.s3utils.bucket`)
* See below: You must pass the correct handler name to the mmisc. operation you will be using.

## Features
### Operations
The plugin contributes the following operations to be used in an Automation Chain.

**WARNING**: For security reason, the operations that read/write S3 are, by default, restricted to administrators when called from REST. See below the details and how to tune the behavior.

* **Files > S3 Utils: Upload** (ID: `S3Utils.Upload`)
  * Upload a file to S3
  * Accepts `Document` or `Blob`, returns the input unchanged
  * Parameters:
    * `handlerName`: The name of the S3Handler to use (see examples above)
    * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
    * `key`: The key to use for S3 storage
    * `xpath`: When the input is `Document`, the field to use. Default value is the main blob, `file:content`.


* **Files > S3 Utils: Download** (ID: `S3Utils.Download`)
  * Input is `void`, downloads a file from S3, returns a `Blob` of the file
  * Parameters:
    * `handlerName`: The name of the S3Handler to use (see examples above)
    * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
    * `key`: The key of the file on S3


* **Files > S3 Utils: Delete** (ID: `S3Utils.Delete`)
  * Input is `void`, deletes a file from S3, returns void
  * Sets a new context variable with the result: `s3UtilsDeletionResult` will contain `"true"` if the key was deleted on S3, or `"false"` if it could not be deleted.
  * Parameters:
    * `handlerName`: The name of the S3Handler to use (see examples above)
    * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
    * `key`: The key of the file on S3

* **Files > S3 Utils: Key Exists** (ID: `S3Utils.KeyExists`)
  * Input is `void`, returns `void`
  * Sets a new context variable with the result: `s3UtilsKeyExists`. It is a `boolean` variable, set to `true` or `false` depending on the result of the test.
  * Parameters:
    * `key`: The key of the file on S3 (required)
    * `handlerName`: The name of the S3Handler to use (see examples above). optional.
    * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
    * `useCache`: Optional, default is `false`. If the S3Handler has been configured to cache the results of KeyExists, it will first search in the cache. If you need to make 100% a key exists or not at the time of the call, ignore this parameter

* **Files > S3 Utils: Temp Signed URL** (ID: `S3Utils.S3TempSignedUrlOp`)
  * Input is `void`, returns `void`
  * Sets a new context variable with the result: `s3UtilsTempSignedUrl`. It is a `String` variable, containing the temporary signed URL.
  * Parameters:
    * `key`: The key of the file on S3 (required)
    * `handlerName`: The name of the S3Handler to use (see examples above). optional.
    * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
    * `durationInSeconds`: Optional, default is set to the value found in the S3Handler configuration.
    * `contentType`: Optional, String.
    * `contentDisposition`: Optional, String.<br/>
      `contentType` and `contentDisposition` are optional but it is recommended to set them to make sure the is no ambiguity when the URL is used (a key without a file extension for example)

* **Files > S3 Utils: Get Object Metadata** (ID: `S3Utils.GetObjectMetadata`)
  * Input is `void`, returns `blob`
  * Returns a JsonBlob holding the metadata (fetching the metadata without fetching the object)
  * Parameters:
    * `key`: The key of the file on S3 (required)
    * `handlerName`: The name of the S3Handler to use. Optional.
  * **IMPORTANT**: If `key` is not found, returns an empty object (`{}`)
  * Else, returns:
    * The system metadata ("Content-Type", "Content-Length", "ETag", ...)
    * Plus some other properties: `bucket`, `key` and `userMetadata`, which is a Json object (can be empty) holding all the user metadata for the object.
  * Notice: When a metadata is not set, it is not returned by AWS (for example, Content-Encoding, of md5 are not always there)

#### How to Import these Operations in your Project?
The principles are:

* Get the JSON definition of the operation(s) you need
* Add it to the "Operations Registry" in Studio

You can find an example here: https://doc.nuxeo.com/nxdoc/how-to-use-pdf-conversion-operations-with-studio/.

#### How to Tune the REST Filtering
The opérations that are filtered for REST calls and restricted to administrators are listed in the `s3-utils-operations.xml` file. We are using the recommended mechanism for the filtering, as described [here](https://doc.nuxeo.com/nxdoc/filtering-exposed-operations/), and the `s3-utils-operations.xml` contains the following:

```
<component name="org.nuxeo.s3-utils.operations">
  . . .
  <extension target="org.nuxeo.ecm.automation.server.AutomationServer" point="bindings">
    <binding name="S3Utils.Delete">
      <administrator>true</administrator>
    </binding>
    . . . etc . . .
  </extension>
</component>
```

This contribution makes sure these single operations, when called via REST, can only be called by a administrator. If you need to make them available via REST for other users/groups, you can:

* Override them in a Studio XML extension
* Use them in an Automation Chain that you call from REST


### Temporary Signed URL

#### The `S3TempSignedURLBuilder` Class 
The `S3TempSignedURLBuilder` class lets you build a temporary signed URL to an S3 object. As you can see in the JavaDoc and/or in the source, you can get such URL passing just the distant object Key (which is, basically, its relative path). You can also use more parameters: The bucket, the duration (in seconds), the content-type and content-disposition.

Content-Type and Content-Disposition should be used, especially if the distant object has no file extension.

The class also has a utility to test the existence of a key on S3.


## Build and Install

Assuming [maven](http://maven.apache.org/) is installed on your system, after downloading the whole repository, execute the following:


* Installation with unit-test (recommended):
  * Add an `aws-test.conf` file containing the required information (region, S3 bucket, file to test and its size). See details in `SimpleFeatureCustom`
  * Be pre-authenticated in the terminal
  * Then, in the terminal, run

  ```
  cd /path/to/nuxeo-s3-utils
  mvn clean install
  ```


* Installation with no unit test:

  ```
  cd /path/to/nuxeo-s3-utils
  mvn clean install -DskipTests=true
  ```


The NuxeoPackage is in `nuxeo-s3-utils-mp/target`, named `nuxeo-s3-utils-mp-{version}.zip`. It can be [installed from the Admin Center](https://doc.nuxeo.com/x/moFH) (see the "Offline Installation" topic), or from the commandline using `nuxeoctl mp-install`.



## Licensing

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)


## Support
These features are not part of the Nuxeo Production platform.

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into platform, not maintained here.

## About Nuxeo
Nuxeo Platform is an open source Content Services platform, written in Java. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/), and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.nuxeo.com](https://www.nuxeo.com).
