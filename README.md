# aws-sdk-android-ktx

# ⚠️ Deprecation Notice

This repository is **deprecated** and is no longer being actively maintained. 
Please migrate to and use the following official SDKs:

- [Amplify for Android](https://github.com/aws-amplify/amplify-android)
- [AWS IoT Device SDK for Java v2](https://github.com/aws/aws-iot-device-sdk-java-v2)

---

[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/crow-misia/aws-sdk-android-ktx/build.yml)](https://github.com/crow-misia/aws-sdk-android-ktx/actions/workflows/build.yml)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.crow-misia.aws-sdk-android-ktx/aws-sdk-android-core-ktx)](https://central.sonatype.com/namespace/io.github.crow-misia.aws-sdk-android-ktx)
[![GitHub License](https://img.shields.io/github/license/crow-misia/aws-sdk-android-ktx)](LICENSE)

AWS SDK for Android with Kotlin.

## Get Started

### Gradle

Add dependencies (you can also add other modules that you need):

`${latest.version}` is [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.crow-misia.aws-sdk-android-ktx/aws-sdk-android-core-ktx)](https://central.sonatype.com/namespace/io.github.crow-misia.aws-sdk-android-ktx)

```groovy
dependencies {
    implementation "io.github.crow-misia.aws-sdk-android-ktx:aws-sdk-android-amplify-ktx:${latest.version}"
    implementation "io.github.crow-misia.aws-sdk-android-ktx:aws-sdk-android-appsync-ktx:${latest.version}"
    implementation "io.github.crow-misia.aws-sdk-android-ktx:aws-sdk-android-core-ktx:${latest.version}"
    implementation "io.github.crow-misia.aws-sdk-android-ktx:aws-sdk-android-iot-ktx:${latest.version}"
}
```

Make sure that you have either `mavenCentral()` in the list of repositories:

```
repository {
    mavenCentral()
}
```

### Create certification and key

```shell
$ aws iot create-keys-and-certificate --set-as-active --certificate-pem-outfile certificate.pem --public-key-outfile public.key --private-key-outfile private.key
```

### endpoint

```shell
$ aws iot describe-endpoint --endpoint-type iot:Data-ATS
```

### 

## License

```
Copyright 2021, Zenichi Amano.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
