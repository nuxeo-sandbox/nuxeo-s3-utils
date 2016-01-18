# nuxeo-s3-utils

This add-on for [Nuxeo](http://www.nuxeo.com) contains utilities for AWS S3

# Features

## Setup
In order to access your AWS S3, the following properties _must_ be set in the configuration file (nuxeo.conf):

```
nuxeo.aws.s3utils.keyid=HERE_THE_KEY_ID
nuxeo.aws.s3utils.secret=HERE_THE_SECRET_KEY
```

You can also set a bucket name, that will be the default bucket, used when, in some API, no bucket is used in the parameters:

```
nuxeo.aws.s3utils.bucket=HERE_THE_BUCKET
```

Last, you cans etup a default duration, in seconds, for the URL:

```
# One hour:
nuxeo.aws.s3utils.duration=3600
```

NOTE: If this value is not set and no duration is passed to some API, an internal default value of 20 minutes applies.




## Temporary Signed URL

### The `S3TempSignedURLBuilder` Class 
The `S3TempSignedURLBuilder` class lets you build a temporary signed URL to an S3 object. As you can see in the JavaDoc and/or in the source, you can get such URL passing just the distant object Key (which is, basiclaly, its relative path). You can also use more parameters: The bucket, the duration (in seconds), the content-type and content-disposition.

Content-Type and Content-Disposition should be used, especially if the distant object has no file extension.


### The `s3utilsHelper` Bean

This bean exposes the following functions to be typically used in an XHTML file:

* `#{s3UtilsHelper.getS3TempSignedUrl("objectKey")}`

  Returns the temp. signed url for the given object key, using the bucket and duration set in the configuration file, and no content type, no content disposition.

* `#{s3UtilsHelper.getS3TempSignedUrl("bucket", "objectKey")}`

  Returns the temp. signed url for the given object key in the given bucket, using the duration set in the configuraiton file, and no content type, no content disposition.

* `#{s3UtilsHelper.getS3TempSignedUrl("bucket", "objectKey", duration, "content type", "content disposition")}`

  This is the call with all the parameters. Default values will apply: if `busket` is empty the code uses the busket defined in the configuration, if `duration` is less than 1 the code uses the duration of the configuraiton file or a hard coded duration, and `contentType`and `contentDisposition` can be empty (no default value uses)
  


A typicall use would be a Widget template. This widget would allow the user to download a file from S3, using a temporary signed url to an object whose ID is stored in a field of the current document.

So, for example, say you want to display an hyperlink to the file, and the object key is stored in the `s3info:s3_object_key` custom field. You then create the "s3TempSignedHyperLink.xhtml" file with this content:

```
<div xmlns:c="http://java.sun.com/jstl/core" xmlns:nxl="http://nuxeo.org/nxforms/layout" xmlns:h="http://java.sun.com/jsf/html" xmlns:nxh="http://nuxeo.org/nxweb/html">
	<c:if test="#{widget.mode != 'edit'}">
		<h:outputLink value="#{s3UtilsHelper.getS3TempSignedUrl(field)}" target="_blank">#{widget.label}</h:outputLink>
	</c:if>	 
  	<c:if test="#{widget.mode == 'edit'}">
		<h:inputText value="#{field}" />
	</c:if>	
</div>
```

This file calls the bean's API `#{s3utilsHelper.getS3TempSignedUrl(field)}`, passing it the value of the widget's `field` property (The label displayed for the link is stored in the `•{widget.label}` property). To easily configure this widget, you should use Nuxeo Studio:

* Import the file in the "Widget Template" part of the "Resources" for your project
* Then, in a Tab (for example, but could be in a layout), drop a "Widget Template" and set its properties:
  1. In the "label" property, set the label you want ("Download from S3" for example)
  2. Now, add a field (click the "+" button) and set the value to `s3info:s3_object_key`
  3. Last, click the "Select" button to select your widget template (stored in "resources")

!["Custom-Thumbnails"](https://raw.github.com/nuxeo-sandbox/nuxeo-s3-utils/master/doc-img/Studio-widget-setup.jpg)

Now, save, deploy, test. Notice that if `s3info:s3_object_key` is empty, no error is triggered ()the url will be blank).

In the same manner, you could use one of the other APIs of the bean (hard code some value in the xhtml, or add more fields)

# Build and Install

Assuming [maven](http://maven.apache.org/) (3.2.5) is installed on your system, after downloading the whole repository, execute the following:


* Installation with unit-test (recommended):
  * See the JavaDoc at `nuxeo-s3-utils/nuxeo-s3-utils-plugin/src/test/java/org/nuxeo/s3utils/test/TestS3TempSignedUrl.java`, and add an `aws-test.conf` file containing the required information (AWS key ID, AWS secret key, S3 bucket, file to test and its size)
  * Then, in the teminal, run

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



## License
(C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.

All rights reserved. This program and the accompanying materials
are made available under the terms of the GNU Lesser General Public License
(LGPL) version 2.1 which accompanies this distribution, and is available at
http://www.gnu.org/licenses/lgpl-2.1.html

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.

Contributors:
Thibaud Arguillere (https://github.com/ThibArg)

## About Nuxeo

Nuxeo provides a modular, extensible Java-based [open source software platform for enterprise content management](http://www.nuxeo.com) and packaged applications for Document Management, Digital Asset Management and Case Management. Designed by developers for developers, the Nuxeo platform offers a modern architecture, a powerful plug-in model and extensive packaging capabilities for building content applications.
