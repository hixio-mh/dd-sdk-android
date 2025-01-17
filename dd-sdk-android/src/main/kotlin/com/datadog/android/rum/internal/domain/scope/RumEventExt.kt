/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.model.ActionEvent
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.model.ResourceEvent
import java.util.Locale

internal fun String.toMethod(): ResourceEvent.Method {
    return try {
        ResourceEvent.Method.valueOf(this.toUpperCase(Locale.US))
    } catch (e: IllegalArgumentException) {
        sdkLogger.w("Unable to convert $this to a valid method", e)
        ResourceEvent.Method.GET
    }
}

internal fun String.toErrorMethod(): ErrorEvent.Method {
    return try {
        ErrorEvent.Method.valueOf(this.toUpperCase(Locale.US))
    } catch (e: IllegalArgumentException) {
        sdkLogger.w("Unable to convert $this to a valid method", e)
        ErrorEvent.Method.GET
    }
}

internal fun RumResourceKind.toSchemaType(): ResourceEvent.Type1 {
    return when (this) {
        RumResourceKind.BEACON -> ResourceEvent.Type1.BEACON
        RumResourceKind.FETCH -> ResourceEvent.Type1.FETCH
        RumResourceKind.XHR -> ResourceEvent.Type1.XHR
        RumResourceKind.DOCUMENT -> ResourceEvent.Type1.DOCUMENT
        RumResourceKind.IMAGE -> ResourceEvent.Type1.IMAGE
        RumResourceKind.JS -> ResourceEvent.Type1.JS
        RumResourceKind.FONT -> ResourceEvent.Type1.FONT
        RumResourceKind.CSS -> ResourceEvent.Type1.CSS
        RumResourceKind.MEDIA -> ResourceEvent.Type1.MEDIA
        RumResourceKind.UNKNOWN,
        RumResourceKind.OTHER -> ResourceEvent.Type1.OTHER
    }
}

internal fun RumErrorSource.toSchemaSource(): ErrorEvent.Source {
    return when (this) {
        RumErrorSource.NETWORK -> ErrorEvent.Source.NETWORK
        RumErrorSource.SOURCE -> ErrorEvent.Source.SOURCE
        RumErrorSource.CONSOLE -> ErrorEvent.Source.CONSOLE
        RumErrorSource.LOGGER -> ErrorEvent.Source.LOGGER
        RumErrorSource.AGENT -> ErrorEvent.Source.AGENT
        RumErrorSource.WEBVIEW -> ErrorEvent.Source.WEBVIEW
    }
}

internal fun ResourceTiming.dns(): ResourceEvent.Dns? {
    return if (dnsStart > 0) {
        ResourceEvent.Dns(duration = dnsDuration, start = dnsStart)
    } else null
}

internal fun ResourceTiming.connect(): ResourceEvent.Connect? {
    return if (connectStart > 0) {
        ResourceEvent.Connect(duration = connectDuration, start = connectStart)
    } else null
}

internal fun ResourceTiming.ssl(): ResourceEvent.Ssl? {
    return if (sslStart > 0) {
        ResourceEvent.Ssl(duration = sslDuration, start = sslStart)
    } else null
}

internal fun ResourceTiming.firstByte(): ResourceEvent.FirstByte? {
    return if (firstByteStart > 0) {
        ResourceEvent.FirstByte(duration = firstByteDuration, start = firstByteStart)
    } else null
}

internal fun ResourceTiming.download(): ResourceEvent.Download? {
    return if (downloadStart > 0) {
        ResourceEvent.Download(duration = downloadDuration, start = downloadStart)
    } else null
}

internal fun RumActionType.toSchemaType(): ActionEvent.Type1 {
    return when (this) {
        RumActionType.TAP -> ActionEvent.Type1.TAP
        RumActionType.SCROLL -> ActionEvent.Type1.SCROLL
        RumActionType.SWIPE -> ActionEvent.Type1.SWIPE
        RumActionType.CLICK -> ActionEvent.Type1.CLICK
        RumActionType.CUSTOM -> ActionEvent.Type1.CUSTOM
    }
}
