# nuxeo-s3-utils


This add-on for [Nuxeo](http://www.nuxeo.com) contains utilities for accessing objects in AWS S3 bucket(s):

* *Operations* to upload or download a file, generate a temporary signed URL, etc.
* *Custom blob provider* to handle objects on buckets that are not the Nuxeo binary bucket. They still get indexed, thumbnail calculated etc. Some important restriction though: The goal is to handle existing files in the bucket(s), so upload (update/creation) is not allowed. See below for details and extra features.

# Table of Content
- [Important: Encryption](#important-encryption)
- [Set Up: Configuration](#set-up-configuration)
  * [Principles and Authentication](#principles-and-authentication)
  * [Contribute the S3Utils Service](#contribute-the-s3utils-service)
  * [Use `nuxeo.conf`](#use-nuxeoconf)
  * [Set Up: An Example](#set-up-an-example)
- [Features](#features)
  * [Operations](#operations)
    * [S3Utils.Upload](#s3utilsupload)
    * [S3Utils.Download](#s3utilsdownload)
    * [S3Utils.Delete](#s3utilsdelete)
    * [S3Utils.KeyExists](#s3utilskeyexists)
    * [S3Utils.S3TempSignedUrlOp](#s3utilss3tempsignedurlop)
    * [S3Utils.GetObjectMetadata](#s3utilsgetobjectmetadata)
    * [S3Utils.CreateBlobFromObjectKey](#s3utilscreateblobfromobjectkey)
    * [Import these Operations in your Project](#import-these-operations-in-your-project)
    * [How to Tune the REST Filtering](#how-to-tune-the-rest-filtering)
  * [Blob Provider](#the-s3utils-blob-provider)
    * [Limitations](#limitations)
    * [Usage](#usage)
  * [Java Features](#java-features)
    * [Streaming an Object](#streaming-an-object)
    * [Temporary Signed URL](#temporary-signed-url)
- [Build and Install](#build-and-install)
- [Licensing](#licensing)
- [Support](#support)
- [About Hyland-Nuxeo](#about-nuxeo)



## ⚠️ Important: Encryption
The plugin does not handle custom encryption, with a client key. It reads he object from S3, so, it assumes the object is either encrypted by S3 or not encrypted (Adding a client key would not be complicated, inspiration can be taken from Nuxeo source code of the nuxeo S3 Binary Manager.)


## Set Up: Configuration
### Principles and Authentication
The plugin creates:

* A `S3Handler` tool, that is in charge of performing the actions (download, upload, ...).
* A `S3UtilsBlobProvider` that can be used to handle Blobs linked to an object in a S3 bucket. This BlobProvider uses a `S3Handler` for actions on the bucket. To link a Blob in Nuxeo to an object in your bucket, you will use the `S3Utils.CreateBlobFromObjectKey` operation (see below)

Both connects to S3 using your credentials, a region and a bucket. In order to allow connecting to several buckets (within the same account), the plugin exposes a _service_, allowing you to access different buckets: You contribute as many S3 Handlers (and possibly several `S3UtilsBlobProvider`) as you need, given each of them a unique name.

The plugin uses Nuxeo AWS Credential code to handle authentication (the `NuxeoAWSCredentialsProvider` class), which means you must either:

* Use nuxeo configuration parameters/XML extension point to set up your AWS credentials (see https://doc.nuxeo.com/nxdoc/amazon-s3-online-storage/)
* Be already authenticated and/or you have setup the expected AWS environment variables before starting Nuxeo (or running the unit tests). This would be the case if, for example, Nuxeo is running from an EC2 instance on AWS (and this instance has permission to access the bucket(s))

For example, on way to run unit tests is to (in a terminal):

* Authenticate to AWS (like, run `aws s3 ls` and authenticate)
* Then, run `mvn install`

If Nuxeo is deployed on an EC2 instance on AWS, it automatically gets the authentication and role from the instance.

**IMPORTANT**: Of course, authentication drives permission, you must make sure the account (or the EC2 instance running) has permission to download, upload, delete, ... in the bucket.

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
    
    <!-- REQUIRED
         Multipart upload is always possible.
         These values must be set and cannot be empty, or the start of Nuxeo will fail with a conversion error
         Set it to 0 if you want to use the default AWS SDK values -->
    <minimumUploadPartSize>0</minimumUploadPartSize>
    <multipartUploadThreshold>0</multipartUploadThreshold>
  </s3Handler>
</extension>
```
Replace the values with yours:

* `name`: Required. The unique name of your handler. To be used in some operations
* `class`: Required. Do not change this one, keep`org.nuxeo.s3utils.S3HandlerImpl` (unless you write your own handler, see the code)
* `region`: Required.
  * The region (always required, even if buckets are global)
  * If this property is empty, the plugin reads the region from:
    * The `nuxeo.aws.s3utils.region` configuration parameter
    * If empty, reads from Nuxeo AWS configuration
    * If still empty, reads from the `nuxeo.aws.region` confifguration parameter
* `bucket`: Required. The bucket to use for this S3 account.
* `tempSignedUrlDuration`: Optional.
  * The duration, in seconds, of a temporary signed URL.
  * If not passed or negative, a default value of 1200 (2 minutes) applies
* `useCacheForExistsKey`: Optional.
  * pass `true` or `false`. Tell the plugin to use a cache when checking the existence of a key in the S3 bucket, to avoid calling S3 too often.
  * Default value is `false`
* `minimumUploadPartSize` and `multipartUploadThreshold`
  * **These values must be set and cannot be empty**, or the start of Nuxeo will fail with a conversion error.
  * The plugin uses Amazon S3 TransferManager to optimize uploads and perform a multipart upload when needed
  * Set these values to `0` to use the default values (As in current AWS SDK, 5MB for `minimumUploadPartSize` and 16MB for `multipartUploadThreshold`)
  * `minimumUploadPartSize`: AWS SDK JavaDoc: "Sets the minimum part size for upload parts. Decreasing the minimum part size will cause multipart uploads to be split into a larger number of smaller parts. Setting this value too low can have a negative effect on transfer speeds since it will cause extra latency and network communication for each part."
  * `multipartUploadThreshold `: AWS SDK JavaDoc: "Sets the size threshold, in bytes, for when to use multipart uploads. Uploads over this size will automatically use a multipart upload strategy, while uploads smaller than this threshold will use a single connection to upload the whole object."
  * The default values suit most of cases, but if you network allows for different settings and better performance, you can change the values.

### Use `nuxeo.conf`
It may be interesting to read the value from `nuxeo.conf`. This way, you can deploy the same Studio project in different environments (typically Dev/Test/Prod), each of them using a different set of regions and buckets.

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
		<minimumUploadPartSize>${nuxeo.aws.s3utils.minimumUploadPartSize:=}</minimumUploadPartSize>
    <multipartUploadThreshold>${nuxeo.aws.s3utils.multipartUploadThreshold:=}</multipartUploadThreshold>
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
# Using default values:
nuxeo.aws.s3utils.minimumUploadPartSize=0
nuxeo.aws.s3utils.multipartUploadThreshold=0

```

## Set Up: An Example
Using `nuxeo.conf` and XML Extension.

We are going to setup 2 handlers accessing the same S3 account, same region, but two different buckets.
 
1. In `nuxeo.conf`, we add the following:

```
nuxeo.aws.s3utils.region=eu-west-1
mycompany.s3.bucketOne=the-bucket
mycompany.s3.bucketTwo=the-other-bucket
nuxeo.aws.s3utils.minimumUploadPartSize=0
nuxeo.aws.s3utils.multipartUploadThreshold=0
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
	 <minimumUploadPartSize>${nuxeo.aws.s3utils.minimumUploadPartSize:=}</minimumUploadPartSize>
    <multipartUploadThreshold>${nuxeo.aws.s3utils.multipartUploadThreshold:=}</multipartUploadThreshold>
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
    <minimumUploadPartSize>${nuxeo.aws.s3utils.minimumUploadPartSize:=}</minimumUploadPartSize>
    <multipartUploadThreshold>${nuxeo.aws.s3utils.multipartUploadThreshold:=}</multipartUploadThreshold>
  </s3Handler>
</extension>
```

Now, we can use the "S3-Bucket-one" or the "S3-Bucket-Two" handlers.

⚠️ In this example, we don't set the values for the `default` handler ⚠️

* It cannot be used (using it will fail, no bucket is defined in `nuxeo.aws.s3utils.bucket`)
* See below: You must pass the correct handler name to the mmisc. operation you will be using.

## Features
* Operations
* Blob Provider
* Java features (like streaming from S3 without downloading)

### Operations
The plugin contributes the following operations to be used in an Automation Chain.

**WARNING**: For security reason, the operations that read/write S3 are, by default, restricted to administrators when called from REST. See below the details and how to tune the behavior.

#### `S3Utils.Upload`
* Label: `Files > S3 Utils: Upload`
* Upload a file to S3
* Accepts `Document` or `Blob`, returns the input unchanged
* Parameters:
  * `handlerName`: The name of the S3Handler to use (see examples above)
  * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
  * `key`: The key to use for S3 storage
  * `xpath`: When the input is `Document`, the field to use. Default value is the main blob, `file:content`.
* *Notice* Upload uses Amazon `TransferManager` and will perform multipart uploads depending on the values set in the configuration (`minimumUploadPartSize`  and `multipartUploadThreshold`). See explanations above.

#### `S3Utils.Download`
* Label: `Files > S3 Utils: Download`
* Input is `void`, downloads a file from S3, returns a `Blob` of the file
* Parameters:
  * `handlerName`: The name of the S3Handler to use (see examples above)
  * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
  * `key`: The key of the file on S3
* *Notice* Download uses Amazon `TransferManager` and will perform multipart downloads when possible.


#### `S3Utils.Delete`
* Label: `Files > S3 Utils: Delete`
* Input is `void`, deletes a file from S3, returns void
* Sets a new context variable with the result: `s3UtilsDeletionResult` will contain `"true"` if the key was deleted on S3, or `"false"` if it could not be deleted.
* Parameters:
  * `handlerName`: The name of the S3Handler to use (see examples above)
  * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
  * `key`: The key of the file on S3

#### `S3Utils.KeyExists`
* Label: `Files > S3 Utils: Key Exists`
* Input is `void`, returns `void`
* Sets a new context variable with the result: `s3UtilsKeyExists`. It is a `boolean` variable, set to `true` or `false` depending on the result of the test.
* Parameters:
  * `key`: The key of the file on S3 (required)
  * `handlerName`: The name of the S3Handler to use (see examples above). optional.
  * `bucket`: Optional. The bucket to use. *Notice*: For advanced usage, when configuring a handler with dynamic buckets (not hard coded in the configuration for example)
  * `useCache`: Optional, default is `false`. If the S3Handler has been configured to cache the results of KeyExists, it will first search in the cache. If you need to make 100% a key exists or not at the time of the call, ignore this parameter

#### `S3Utils.S3TempSignedUrlOp`
* Label: `Files > S3 Utils: Temp Signed URL`
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

#### `S3Utils.GetObjectMetadata`
* Label: `Files > S3 Utils: Get Object Metadata`
* Input is `void`, returns `blob`
* Returns a JsonBlob holding the metadata (fetching the metadata without fetching the object)
* Parameters:
  * `key`: The key of the file on S3 (required)
  * `handlerName`: The name of the S3Handler to use. Optional.
* **IMPORTANT**: If `key` is not found, returns an empty object (`{}`)
* Else, returns:
  * The system metadata ("Content-Type", "Content-Length", "ETag", ...)
  * Plus some other properties: `"bucket"`, `"key"` and `"userMetadata"`, which is a Json object (can be empty) holding all the user metadata for the object.
* Notice: When a metadata is not set, it is not returned by AWS (for example, Content-Encoding, of md5 are not always there)

#### `S3Utils.CreateBlobFromObjectKey`
* Label: `Files > S3 Utils: Create Blob from Object Key`
* Input is `void`, returns `blob`
* Returns a Blob holding the reference to the remote S3 object. This blob can then be stored in any blob field of a document (typically `file:content`)
* Parameters:
  * `blobProviderId`: required, the BlobProvider ID, as contributed via XML (see an example below, _The S3Utils Blob Provider_)
  * `objectKey`: Required, the key of the object in the S3 bucket.
* Notice you don't specify a bucket. The operation reads the bucket from the `S3Handler` linked to the BlobProvider (see below _The S3Utils Blob Provider_).


#### Import these Operations in your Project
The principles are:

* Get the JSON definition of the operation(s) you need
* Add it to the "Operations Registry" in Studio

You can find an example here: https://doc.nuxeo.com/nxdoc/how-to-use-pdf-conversion-operations-with-studio/.

Or you can use [Nuxeo CLI](https://doc.nuxeo.com/nxdoc/nuxeo-cli/) for this purpose, it can create the registry entries for you.

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

### The S3Utils Blob Provider

The goal of this blob provider is to allow for handling blobs that are not stored by Nuxeo BinaryManager. This way, you can have a Nuxeo document and store, in file:content for example a blob that actually points to your custom bucket. It will then be transparent for the user: Nuxeo will get a thumbnail from it, extract full text and index it etc. For this purpose, it will have, of course, to download the file from S3 (it is then saved to a local, temporary, file cache).

You can (see below) tune the provider so it does not download files bigger than a threshold (imagine you have a 200GB file: downloading it for a thumbnail, fulltext indexing, etc. could be complex and would certainly requires extra storage (for temp. files), extra i/o, configuration and timeout, etc.)

#### Limitations

* **No write**
  * For now, the provider does not allow to upload a blob to you s3 bucket (no new file, no update)
* **No automatical update form the S3 files**: If a file is modified on s3, it is not updated in Nuxeo (no new thumbnail calculated, no fulltext index recalculated, …)


#### Usage

To use the provider, you must just contribute the BlobManager as in the following example:

```
<extension target="org.nuxeo.ecm.core.blob.BlobManager" point="configuration">
  <blobprovider name="TestS3BlobProvider-XML">
    <class>org.nuxeo.s3utils.S3UtilsBlobProvider</class>
    <!-- Here are the default values if you don't set the properties -->
    <property name="cacheSize">100 MB</property>
    <property name="cacheCount">10000</property>
    <property name="cacheMinAge">3600</property>
    <property name="s3Handler">default</property>
    <property name="noDefaultDownloadAbove">0</property>
  </blobprovider>
  </extension>
```

* `class` is required and must be exactly `org.nuxeo.s3utils.S3UtilsBlobProvider`
* `cacheSize`, `cacheCount` and `cacheMinAge`: Optional. Handle the file cache, so when a file is downloaded from s3, it is cached, so if it is required later, it is already there.
  * The cache is a LRU cache (Least Recent Update cache), and default values are "100 MB" for `cacheSize`, "10000" for `cacheCount`, and one hour ("3600") for `cacheMinAge`
* `s3Handler`: optional. The name of the related `S3Handler`. It will be used to get the bucket and authentication etc. Not passed => use the default handler.
* `noDefaultDownloadAbove`: Optional.
  * A number, in bytes, above which the action of downloading the blob will actually not download it, but download a place holder instead, containing just the basic info (file name, file size, mime type). These infos will be returned in a blob, trying to match the mime-ype of the original object, but it can't obviously be always relevant. Handled mime types are text/plain, application/pdf and image/jpeg-png.
  * So, **warning**:
    * thumbnail and full text index will of course not reflect the content of the distant file
    * Errors could occur in the log when Nuxeo tries to get a thumbnail/extract fulltext
  * The main goal of this parameter is to handle big files on S3, to avoid downloading them locally for handling of thumbnails and renditions.
  * Notice you can always handle the thumbnail yourself (Add the `Thumbnail` facet and set an image to `thumb:thumb`for example), the preview (tune your nuxeo-yourdoc-view-layout to display something relevant, etc.


### Java Features

#### Streaming an Object
Both the `S3HandlerImpl` and the `S3UtilsBlobProvider` classes allow for _streaming_ an object form S3. This can be very useful when you don't want/don't need to actually download it. Both classes allow for streaming the whole object or a range.

Please, see the code and its JavaDoc for details, `S3ObjectStreaming` interface and the `getInputStream`and `readBytes` methods.

These features are not available without explicitly calling them in Java though. For example, Nuxeo BlobProvider interface does not handle streaming, so Nuxeo will never try to get a stream from a S3 blob. The purpose of these classes is to allow our prospects/customers (with Java dev. skills of course) to use this code, either as is (as a maven dependency), or by forking it or just copy/pasting the relevant part, to be included in their own plugin(s).

#### Temporary Signed URL
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
Nuxeo Platform is an open source Content Services platform, written in Java and provided by Hyland. Data can be stored in both SQL & NoSQL databases.

The development of the Nuxeo Platform is mostly done by Nuxeo employees with an open development model.

The source code, documentation, roadmap, issue tracker, testing, benchmarks are all public.

Typically, Nuxeo users build different types of information management solutions for [document management](https://www.nuxeo.com/solutions/document-management/), [case management](https://www.nuxeo.com/solutions/case-management/), and [digital asset management](https://www.nuxeo.com/solutions/dam-digital-asset-management/), use cases. It uses schema-flexible metadata & content models that allows content to be repurposed to fulfill future use cases.

More information is available at [www.hyland.copm](https://www.hyland.com).
