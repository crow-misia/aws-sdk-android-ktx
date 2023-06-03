# aws-sdk-android-ktx

[![Android CI](https://github.com/crow-misia/aws-sdk-android-ktx/actions/workflows/android.yml/badge.svg?branch=main)](https://github.com/crow-misia/aws-sdk-android-ktx/actions/workflows/android.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.crow-misia.aws-sdk-android-ktx/aws-sdk-android-iot-ktx.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.crow-misia.aws-sdk-android-ktx%22%20AND%20a:%22aws-sdk-android-iot-ktx%22)
[![License](https://img.shields.io/github/license/crow-misia/aws-sdk-android-ktx)](LICENSE)

AWS SDK for Android with Kotlin.

## Get Started

### Gradle

Add dependencies (you can also add other modules that you need):

`${latest.version}` is [![Download](https://img.shields.io/maven-central/v/io.github.crow-misia.aws-sdk-android-ktx/aws-sdk-android-iot-ktx.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.github.crow-misia.aws-sdk-android-ktx%22%20AND%20a:%22aws-sdk-android-iot-ktx%22)

```groovy
dependencies {
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
