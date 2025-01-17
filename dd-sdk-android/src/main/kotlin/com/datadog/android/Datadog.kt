/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleCallback
import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.error.internal.CrashReportsFeature
import com.datadog.android.log.EndpointUpdateStrategy
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracesFeature
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class initializes the Datadog SDK, and sets up communication with the server.
 */
@Suppress("TooManyFunctions")
object Datadog {

    /**
     * The endpoint for our US based servers, used by default by the SDK.
     * @see [initialize]
     * @deprecated Use the [DatadogEndpoint.LOGS_US] instead
     */
    @Suppress("MemberVisibilityCanBePrivate")
    @Deprecated(
        "Use the DatadogEndpoint.LOGS_US instead",
        ReplaceWith(
            expression = "DatadogEndpoint.LOGS_US",
            imports = ["com.datadog.android.DatadogEndpoint"]
        )
    )
    const val DATADOG_US: String = "https://mobile-http-intake.logs.datadoghq.com"

    /**
     * The endpoint for our Europe based servers.
     * Use this in your call to [initialize] if you log on
     * [app.datadoghq.eu](https://app.datadoghq.eu/) instead of
     * [app.datadoghq.com](https://app.datadoghq.com/)
     * @deprecated Use the [DatadogEndpoint.LOGS_EU] instead
     */
    @Suppress("MemberVisibilityCanBePrivate")
    @Deprecated(
        "Use the DatadogEndpoint.LOGS_EU instead",
        ReplaceWith(
            expression = "DatadogEndpoint.LOGS_EU",
            imports = ["com.datadog.android.DatadogEndpoint"]
        )
    )
    const val DATADOG_EU: String = "https://mobile-http-intake.logs.datadoghq.eu"

    internal val initialized = AtomicBoolean(false)
    internal val startupTimeNs = System.nanoTime()

    internal var libraryVerbosity = Int.MAX_VALUE
        private set
    internal var isDebug = false

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param config the configuration for the SDK library
     * @see [DatadogConfig]
     */
    @Suppress("LongMethod")
    @JvmStatic
    fun initialize(
        context: Context,
        config: DatadogConfig
    ) {
        if (initialized.get()) {
            devLogger.w(MESSAGE_ALREADY_INITIALIZED)
            return
        }

        val appContext = context.applicationContext

        // always initialize Core Features first
        CoreFeature.initialize(appContext, config.coreConfig)

        config.logsConfig?.let { featureConfig ->
            LogsFeature.initialize(
                appContext = appContext,
                config = featureConfig,
                okHttpClient = CoreFeature.okHttpClient,
                networkInfoProvider = CoreFeature.networkInfoProvider,
                systemInfoProvider = CoreFeature.systemInfoProvider,
                dataUploadThreadPoolExecutor = CoreFeature.dataUploadScheduledExecutor,
                dataPersistenceExecutor = CoreFeature.dataPersistenceExecutorService
            )
        }

        config.tracesConfig?.let { featureConfig ->
            TracesFeature.initialize(
                appContext = appContext,
                config = featureConfig,
                okHttpClient = CoreFeature.okHttpClient,
                networkInfoProvider = CoreFeature.networkInfoProvider,
                timeProvider = CoreFeature.timeProvider,
                userInfoProvider = CoreFeature.userInfoProvider,
                systemInfoProvider = CoreFeature.systemInfoProvider,
                dataUploadThreadPoolExecutor = CoreFeature.dataUploadScheduledExecutor,
                dataPersistenceExecutor = CoreFeature.dataPersistenceExecutorService
            )
        }

        config.rumConfig?.let { featureConfig ->
            RumFeature.initialize(
                appContext = appContext,
                config = featureConfig,
                okHttpClient = CoreFeature.okHttpClient,
                networkInfoProvider = CoreFeature.networkInfoProvider,
                systemInfoProvider = CoreFeature.systemInfoProvider,
                dataUploadThreadPoolExecutor = CoreFeature.dataUploadScheduledExecutor,
                dataPersistenceExecutor = CoreFeature.dataPersistenceExecutorService,
                userInfoProvider = CoreFeature.userInfoProvider
            )
        }

        config.crashReportConfig?.let { featureConfig ->
            CrashReportsFeature.initialize(
                appContext = appContext,
                config = featureConfig,
                okHttpClient = CoreFeature.okHttpClient,
                networkInfoProvider = CoreFeature.networkInfoProvider,
                timeProvider = CoreFeature.timeProvider,
                userInfoProvider = CoreFeature.userInfoProvider,
                systemInfoProvider = CoreFeature.systemInfoProvider,
                dataUploadThreadPoolExecutor = CoreFeature.dataUploadScheduledExecutor
            )
        }

        setupLifecycleMonitorCallback(appContext)

        isDebug = resolveIsDebug(context)

        initialized.set(true)

        // Issue #154 (“Thread starting during runtime shutdown”)
        // Make sure we stop Datadog when the Runtime shuts down
        Runtime.getRuntime()
            .addShutdownHook(
                Thread(Runnable { stop() }, SHUTDOWN_THREAD)
            )
    }

    /**
     * Initializes the Datadog SDK.
     * @param context your application context
     * @param clientToken your API key of type Client Token
     * @param envName the environment special attribute which will be sent with each event.
     * (e.g. "staging" vs. "production")
     * @param endpointUrl (optional) the endpoint url to target, or null to use the default. Possible values are
     * [DATADOG_US], [DATADOG_EU] or a custom endpoint.
     * @deprecated Use the [initialize] method with a [DatadogConfig] instance
     */
    @JvmStatic
    @JvmOverloads
    @Deprecated("Use the initialize method with a DatadogConfig instance")
    fun initialize(
        context: Context,
        clientToken: String,
        envName: String,
        endpointUrl: String? = null
    ) {
        val config = DatadogConfig.Builder(clientToken, envName)
            .useCustomLogsEndpoint(endpointUrl ?: DatadogEndpoint.LOGS_US)
            .useCustomCrashReportsEndpoint(endpointUrl ?: DatadogEndpoint.LOGS_US)
            .build()
        initialize(context, config)
    }

    /**
     * Changes the endpoint to which logging data is sent.
     * @param endpointUrl the endpoint url to target, or null to use the default.
     * Possible values are [DATADOG_US_LOGS], [DATADOG_EU_LOGS] or a custom endpoint.
     * @param strategy the strategy defining how to handle logs created previously.
     * Because logs are sent asynchronously, some logs intended for the previous endpoint
     * might still be yet to sent.
     */
    @Suppress("DeprecatedCallableAddReplaceWith")
    @JvmStatic
    @Deprecated("This was only meant as an internal feature and is not needed anymore.")
    fun setEndpointUrl(endpointUrl: String, strategy: EndpointUpdateStrategy) {
        devLogger.w(String.format(Locale.US, MESSAGE_DEPRECATED, "setEndpointUrl()"))
    }

    /**
     * Checks if the Datadog SDK was already initialized.
     * @return true if the SDK was initialized, false otherwise
     */
    fun isInitialized(): Boolean {
        return initialized.get()
    }

    // Stop all Datadog work (for test purposes).
    @Suppress("unused")
    private fun stop() {
        if (initialized.get()) {
            LogsFeature.stop()
            TracesFeature.stop()
            RumFeature.stop()
            CrashReportsFeature.stop()
            CoreFeature.stop()
            isDebug = false
            initialized.set(false)
        }
    }

    /**
     * Sets the verbosity of the Datadog library.
     *
     * Messages with a priority level equal or above the given level will be sent to Android's
     * Logcat.
     *
     * @param level one of the Android [Log] constants ([Log.VERBOSE], [Log.DEBUG], [Log.INFO],
     * [Log.WARN], [Log.ERROR], [Log.ASSERT]).
     */
    @JvmStatic
    fun setVerbosity(level: Int) {
        libraryVerbosity = level
    }

    /**
     * Sets the user information.
     *
     * @param id (nullable) a unique user identifier (relevant to your business domain)
     * @param name (nullable) the user name or alias
     * @param email (nullable) the user email
     */
    @JvmStatic
    @JvmOverloads
    fun setUserInfo(
        id: String? = null,
        name: String? = null,
        email: String? = null
    ) {
        CoreFeature.userInfoProvider.setUserInfo(UserInfo(id, name, email))
    }

    // region Internal Initialization

    private fun setupLifecycleMonitorCallback(appContext: Context) {
        if (appContext is Application) {
            val callback = ProcessLifecycleCallback(CoreFeature.networkInfoProvider, appContext)
            appContext.registerActivityLifecycleCallbacks(ProcessLifecycleMonitor(callback))
        }
    }

    private fun resolveIsDebug(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    internal const val MESSAGE_ALREADY_INITIALIZED =
        "The Datadog library has already been initialized."
    internal const val MESSAGE_NOT_INITIALIZED = "Datadog has not been initialized.\n" +
            "Please add the following code in your application's onCreate() method:\n" +
            "Datadog.initialize(context, \"<CLIENT_TOKEN>\");"

    internal const val MESSAGE_DEPRECATED = "%s has been deprecated. " +
            "If you need it, submit an issue at https://github.com/DataDog/dd-sdk-android/issues/"

    internal const val SHUTDOWN_THREAD = "datadog_shutdown"

    // endregion
}
