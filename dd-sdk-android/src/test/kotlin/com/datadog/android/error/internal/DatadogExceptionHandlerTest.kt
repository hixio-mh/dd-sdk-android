/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.impl.WorkManagerImpl
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.core.internal.data.upload.UploadWorker
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.TAG_DATADOG_UPLOAD
import com.datadog.android.core.internal.utils.UPLOAD_WORKER_NAME
import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.invokeMethod
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogExceptionHandlerTest {

    var originalHandler: Thread.UncaughtExceptionHandler? = null

    lateinit var testedHandler: DatadogExceptionHandler

    @Mock
    lateinit var mockPreviousHandler: Thread.UncaughtExceptionHandler

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockUserInfoProvider: UserInfoProvider

    @Mock
    lateinit var mockLogWriter: Writer<Log>

    @Mock
    lateinit var mockWorkManager: WorkManagerImpl

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Forgery
    lateinit var fakeThrowable: Throwable

    @Forgery
    lateinit var fakeNetworkInfo: NetworkInfo

    @Forgery
    lateinit var fakeUserInfo: UserInfo

    @Forgery
    lateinit var fakeTime: Date

    @BeforeEach
    fun `set up`(forge: Forge) {
        whenever(mockNetworkInfoProvider.getLatestNetworkInfo()) doReturn fakeNetworkInfo
        whenever(mockUserInfoProvider.getUserInfo()) doReturn fakeUserInfo
        whenever(mockTimeProvider.getServerTimestamp()) doReturn fakeTime.time

        val mockContext: Application = mockContext()
        Datadog.initialize(mockContext, forge.anAlphabeticalString(), forge.anHexadecimalString())
        GlobalRum.registerIfAbsent(mockRumMonitor)

        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(mockPreviousHandler)
        testedHandler = DatadogExceptionHandler(
            mockNetworkInfoProvider,
            mockTimeProvider,
            mockUserInfoProvider,
            mockLogWriter,
            mockContext
        )
        testedHandler.register()
    }

    @AfterEach
    fun `tear down`() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", null)
        Datadog.invokeMethod("stop")
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `log exception when caught with no previous handler`(forge: Forge) {
        val envName = forge.anAlphaNumericalString()
        CrashReportsFeature.envName = envName
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestamp(fakeTime.time)
                .hasTags(listOf("env:$envName"))
        }
        verify(mockRumMonitor).addCrash(
            "Application crash detected",
            RumErrorSource.SOURCE,
            fakeThrowable
        )
        verifyZeroInteractions(mockPreviousHandler)
    }

    @Test
    fun `schedules the worker when logging an exception`(forge: Forge) {
        WorkManagerImpl::class.java.setStaticValue("sDefaultInstance", mockWorkManager)
        Thread.setDefaultUncaughtExceptionHandler(null)
        testedHandler.register()
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        verify(mockWorkManager)
            .enqueueUniqueWork(
                eq(UPLOAD_WORKER_NAME),
                eq(ExistingWorkPolicy.REPLACE),
                argThat<OneTimeWorkRequest> {
                    this.workSpec.workerClassName == UploadWorker::class.java.canonicalName &&
                            this.tags.contains(TAG_DATADOG_UPLOAD)
                })
    }

    @Test
    fun `log exception when caught`(forge: Forge) {
        val envName = forge.anAlphaNumericalString()
        CrashReportsFeature.envName = envName
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestamp(fakeTime.time)
                .hasTags(listOf("env:$envName"))
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @Test
    fun `log exception when caught without env name`(forge: Forge) {
        CrashReportsFeature.envName = ""
        val currentThread = Thread.currentThread()

        testedHandler.uncaughtException(currentThread, fakeThrowable)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(currentThread.name)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestamp(fakeTime.time)
                .hasTags(emptyList())
        }
        verify(mockPreviousHandler).uncaughtException(currentThread, fakeThrowable)
    }

    @Test
    fun `log exception when caught on background thread`(forge: Forge) {
        val envName = forge.anAlphaNumericalString()
        CrashReportsFeature.envName = envName
        val latch = CountDownLatch(1)
        val threadName = forge.anAlphabeticalString()
        val thread = Thread({
            testedHandler.uncaughtException(Thread.currentThread(), fakeThrowable)
            latch.countDown()
        }, threadName)
        thread.start()

        latch.await(1, TimeUnit.SECONDS)

        argumentCaptor<Log> {
            verify(mockLogWriter).write(capture())
            assertThat(lastValue)
                .hasThreadName(threadName)
                .hasMessage("Application crash detected")
                .hasLevel(Log.CRASH)
                .hasThrowable(fakeThrowable)
                .hasNetworkInfo(fakeNetworkInfo)
                .hasUserInfo(fakeUserInfo)
                .hasTimestamp(fakeTime.time)
                .hasTags(listOf("env:$envName"))
        }
        verify(mockPreviousHandler).uncaughtException(thread, fakeThrowable)
    }
}
