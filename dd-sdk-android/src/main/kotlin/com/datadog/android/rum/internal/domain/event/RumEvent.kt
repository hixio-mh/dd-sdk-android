/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.log.internal.user.UserInfo

internal data class RumEvent(
    val event: Any,
    val attributes: Map<String, Any?>,
    val userInfo: UserInfo? = null,
    val networkInfo: NetworkInfo? = null
)
