/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import android.os.Bundle
import com.datadog.android.core.internal.utils.runIfValid
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.internal.tracking.ViewLoadingTimer

/**
 * A [ViewTrackingStrategy] that will track [Activity] as RUM views.
 *
 * Each activity's lifecycle will be monitored to start and stop RUM views when relevant.
 * @param trackExtras whether to track Activity Intent extras
 * @param componentPredicate to accept the Activities that will be taken into account as
 * valid RUM View events.
 */
class ActivityViewTrackingStrategy @JvmOverloads constructor(
    private val trackExtras: Boolean,
    private val componentPredicate: ComponentPredicate<Activity> = AcceptAllActivities()
) :
    ActivityLifecycleTrackingStrategy(),
    ViewTrackingStrategy {

    private val viewLoadingTimer = ViewLoadingTimer()

    // region ActivityLifecycleTrackingStrategy

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        super.onActivityCreated(activity, savedInstanceState)
        componentPredicate.runIfValid(activity) {
            viewLoadingTimer.onCreated(it)
        }
    }

    override fun onActivityStarted(activity: Activity) {
        super.onActivityStarted(activity)
        componentPredicate.runIfValid(activity) {
            viewLoadingTimer.onStartLoading(it)
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        componentPredicate.runIfValid(activity) {
            val javaClass = it.javaClass
            val vieName = javaClass.canonicalName ?: javaClass.simpleName
            val attributes =
                if (trackExtras) convertToRumAttributes(it.intent?.extras) else emptyMap()
            GlobalRum.monitor.startView(
                it,
                vieName,
                attributes
            )
        }
    }

    override fun onActivityPostResumed(activity: Activity) {
        super.onActivityPostResumed(activity)
        componentPredicate.runIfValid(activity) {
            viewLoadingTimer.onFinishedLoading(it)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        componentPredicate.runIfValid(activity) {
            updateLoadingTime(activity)
            GlobalRum.monitor.stopView(it)
            viewLoadingTimer.onPaused(activity)
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        super.onActivityDestroyed(activity)
        componentPredicate.runIfValid(activity) {
            viewLoadingTimer.onDestroyed(it)
        }
    }

    // endregion

    // region Internal

    private fun updateLoadingTime(activity: Activity) {
        viewLoadingTimer.getLoadingTime(activity)?.let { loadingTime ->
            val advancedRumMonitor = GlobalRum.get() as? AdvancedRumMonitor
            advancedRumMonitor?.let { monitor ->
                val loadingType = resolveLoadingType(viewLoadingTimer.isFirstTimeLoading(activity))
                monitor.updateViewLoadingTime(
                    activity,
                    loadingTime,
                    loadingType
                )
            }
        }
    }

    private fun resolveLoadingType(firstTimeLoading: Boolean): ViewEvent.LoadingType {
        return if (firstTimeLoading) {
            ViewEvent.LoadingType.ACTIVITY_DISPLAY
        } else {
            ViewEvent.LoadingType.ACTIVITY_REDISPLAY
        }
    }

    // endregion
}
