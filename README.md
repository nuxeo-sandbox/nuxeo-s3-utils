# nuxeo-s3-utils

QA build status: [![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-s3-utils-master)](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-s3-utils-master)

This add-on for [Nuxeo](http://www.nuxeo.com) contains utilities for AWS S3: Operations to upload or download a file, utilities to build a temporary signed URL to S3, and a helper SEAM Bean.

## Set Up: Configuration
### Principles
The plugin creates a `S3Handler` tool, that is in charge of performing the actions (download, upload, ...). It connects to S3 using your key ID, secret key and the S3 bucket.

In order to allow connecting to several S3 accounts, and inside an S3 account, to several buckets, the plugin exposes a _service_, allowing you to access different accounts and buckets: You contribute as many S3 Handlers as you need, given each of them a unique name.

### Contribute the S3Utils Service
Fo each S3 account and bucket you want to access, just add the following contribution to your Nuxeo Studio project (Advanced Settings > XML Extension). Values are explain below.

```
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>HANDLER_NAME</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <awsKey>YOUR_KEY</awsKey>
    <awsSecret>YOUR_SECRET</awsSecret>
    <bucket>THE_BUCKET</bucket>
    <tempSignedUrlDuration>DURATION_IN_SECONDS</tempSignedUrlDuration>
    <useCacheForExistsKey>true or flase</useCacheForExistsKey>
  </s3Handler>
</extension>
```
Replace the values with yours:

* `name`: The unique name of your handler. To be used in some operations
* `class`: Do not change this one, keep`org.nuxeo.s3utils.S3HandlerImpl` (unless you write your own handler, see the code)
* `awsKey`: The key to access your S3 account
* `awsSecret`: The secret to access your S3 account
* `bucket`: The bucket to use for this S3 account
* `tempSignedUrlDuration`:
  * The duration, in seconds, of a temporary signed URL.
  * Optional: If not passed or negative, a default value of 1200 (2 minutes) applies
* `useCacheForExistsKey`:
  * pass `true` or `false`. Tell the plugin to use a cache when checking the existence of a key in the S3 bucket, to avoid calling S3 too often.
  * Default value is `false`

### Use `nuxeo.conf`
It may be interesting to read the value from `nuxeo.conf`. This way, you can deploy the same Studio projet in different environments (typically Dev/Test/Prod), each of them using a different set of keys and buckets.

For this purpose:

1. Declare your custom configuration parameters in nuxeo.conf
2. Use them in the XML declaration, using the templating language: `${your.custom.key:=}`

## Set Up: An Example
Using `nuxeo.conf` and XML Extension.

We are going to setup 2 handlers accessing the same S3 account, but two different buckets.
 
1. In `nuxeo.conf`, we add the following:

```
mycompany.s3.keyid=123456
mycompany.s3.secret=HERE_THE_SECRET_KEY
mycompany.s3.bucketOne=the-bucket
mycompany.s3.bucketTwo=the-other-bucket
```

2. In our Studio project, we create two XML extension
  * First one (notice: Give whatever name you want to the XML Extension itself) uses the key, the secret and "the-bucket"

```
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>S3-Bucket-one</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <awsKey>${mycompany.s3.keyid:=}</awsKey>
    <awsSecret>${mycompany.s3.secret:=}</awsSecret>
    <bucket>${mycompany.s3.bucketOne:=}</bucket>
    <!-- Let default values for other parameters -->
  </s3Handler>
</extension>
```

 * Second one uses the key, the secret, "the-other-bucket", and only one minute of duration for the temporary signed URLs:

```
<!-- Let default values for other parameters -->
<extension target="org.nuxeo.s3utils.service" point="configuration">
  <s3Handler>
    <name>S3-Bucket-Two</name>
    <class>org.nuxeo.s3utils.S3HandlerImpl</class>
    <awsKey>${mycompany.s3.keyid:=}</awsKey>
    <awsSecret>${mycompany.s3.secret:=}</awsSecret>
    <bucket>${mycompany.s3.bucketTwo:=}</bucket>
    <tempSignedUrlDuration>60</tempSignedUrlDuration>
  </s3Handler>
</extension>
```

Now, we can use the "S3-Bucket-one" or the "S3-Bucket-Two" handlers.

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


#### The `s3utilsHelper` Bean

This bean exposes the following functions to be typically used in an XHTML file:

* `#{s3UtilsHelper.getS3TempSignedUrl("objectKey")}`

  Returns the temp. signed url for the given object key, using the bucket and duration set in the configuration file, and no content type, no content disposition.

* `#{s3UtilsHelper.getS3TempSignedUrl("bucket", "objectKey")}`

  Returns the temp. signed url for the given object key in the given bucket, using the duration set in the configuraiton file, and no content type, no content disposition.

* `#{s3UtilsHelper.getS3TempSignedUrl("bucket", "objectKey", duration, "content type", "content disposition")}`

  This is the call with all the parameters. Default values will apply: if `busket` is empty the code uses the busket defined in the configuration, if `duration` is less than 1 the code uses the duration of the configuration file or a hard coded duration, and `contentType`and `contentDisposition` can be empty (no default value uses)
  
* `#{s3UtilsHelper.existsKey("objectKey")` and `#{s3UtilsHelper.existsKey("bucket", "objectKey")`
  
  Utility returning `true` if the given object exists in S3. To avoid multiple REST calls to AWS for the same object, the plug-in caches the value for 10 minutes (bucket + object key => exists or not).
  


A typical use would be a Widget template. This widget would allow the user to download a file from S3, using a temporary signed url to an object whose ID is stored in a field of the current document.

So, for example, say you want to display an hyperlink to the file, and the object key is stored in the `s3info:s3_object_key` custom field. You then create the "s3TempSignedHyperLink.xhtml" file with this content:

```
<div xmlns:c="http://java.sun.com/jstl/core"
     xmlns:nxl="http://nuxeo.org/nxforms/layout"
     xmlns:h="http://java.sun.com/jsf/html"
     xmlns:nxh="http://nuxeo.org/nxweb/html">
	<c:if test="#{widget.mode != 'edit'}">
		<h:outputLink value="#{s3UtilsHelper.getS3TempSignedUrl(field)}" target="_blank">#{widget.label}</h:outputLink>
	</c:if>	 
  	<c:if test="#{widget.mode == 'edit'}">
		<h:inputText value="#{field}" />
	</c:if>	
</div>
```

This file calls the bean's API `#{s3UtilsHelper.getS3TempSignedUrl(field)}`, passing it the value of the widget's `field` property (The label displayed for the link is stored in the `•{widget.label}` property). To easily configure this widget, you should use Nuxeo Studio:

* Import the file in the "Widget Template" part of the "Resources" for your project
* Then, in a Tab (for example, but could be in a layout), drop a "Widget Template" and set its properties:
  1. In the "label" property, set the label you want ("Download from S3" for example)
  2. Now, add a field (click the "+" button) and set the value to `s3info:s3_object_key`
  3. Last, click the "Select" button to select your widget template (stored in "resources")

!["Custom-Thumbnails"](https://raw.github.com/nuxeo-sandbox/nuxeo-s3-utils/master/doc-img/Studio-widget-setup.jpg)

Now, save, deploy, test. Notice that if `s3info:s3_object_key` is empty, no error is triggered ()the url will be blank).

In the same manner, you could use one of the other APIs of the bean (hard code some value in the xhtml, or add more fields)

Here is another example of XHTML that first tests if the object exists. If yes, it outputs a regular link, else, it outputs just the rwax text:

```
<div
	xmlns:c="http://java.sun.com/jstl/core"
	xmlns:nxl="http://nuxeo.org/nxforms/layout"
	xmlns:h="http://java.sun.com/jsf/html"
	xmlns:nxh="http://nuxeo.org/nxweb/html"
	xmlns:nxu="http://nuxeo.org/nxweb/util">

	<c:if test="#{widget.mode != 'edit'}">
		<nxu:set var="existsKey" value="#{s3UtilsHelper.existsKey(field)}">
			<c:if test="#{existsKey}">
				<a href="#{s3UtilsHelper.getS3TempSignedUrl('', field, 0, '', 'attachment;')}" download="true">#{widget.label}</a>
			</c:if>
			<c:if test="#{!existsKey}">
				#{field}
			</c:if>
		</nxu:set>
	</c:if>

  	<c:if test="#{widget.mode == 'edit'}">
		<h:inputText value="#{field}" />
	</c:if>	
</div>
```

## Build and Install

Assuming [maven](http://maven.apache.org/) (3.2.5) is installed on your system, after downloading the whole repository, execute the following:


* Installation with unit-test (recommended):
  * See the JavaDoc at `nuxeo-s3-utils/nuxeo-s3-utils-plugin/src/test/java/org/nuxeo/s3utils/test/TestS3TempSignedUrl.java`, and add an `aws-test.conf` file containing the required information (AWS key ID, AWS secret key, S3 bucket, file to test and its size)
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
Nuxeo, developer of the leading Content Services Platform, is reinventing enterprise content management (ECM) and digital asset management (DAM). Nuxeo is fundamentally changing how people work with data and content to realize new value from digital information. Its cloud-native platform has been deployed by large enterprises, mid-sized businesses and government agencies worldwide. Customers like Verizon, Electronic Arts, ABN Amro, and the Department of Defense have used Nuxeo's technology to transform the way they do business. Founded in 2008, the company is based in New York with offices across the United States, Europe, and Asia.

Learn more at www.nuxeo.com.