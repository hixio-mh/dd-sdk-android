/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

include(":dd-sdk-android")
include(":dd-sdk-android-timber")
include(":dd-sdk-android-ktx")

include(":instrumented:benchmark")
include(":instrumented:integration")

include(":sample:java")
include(":sample:kotlin")
include(":sample:kotlin-timber")

include(":tools:detekt")
include(":tools:unit")
include(":tools:noopfactory")
