/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.app.Application
import com.datadog.android.Datadog
import com.datadog.android.DatadogConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.domain.AsyncWriterFilePersistenceStrategy
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockContext
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.File
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
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
internal class LogsFeatureTest {

    lateinit var mockAppContext: Application

    @Mock
    lateinit var mockNetworkInfoProvider: NetworkInfoProvider

    @Mock
    lateinit var mockSystemInfoProvider: SystemInfoProvider

    @Mock
    lateinit var mockOkHttpClient: OkHttpClient

    @Mock
    lateinit var mockScheduledThreadPoolExecutor: ScheduledThreadPoolExecutor

    @Mock
    lateinit var mockPersistenceExecutorService: ExecutorService

    lateinit var fakeConfig: DatadogConfig.FeatureConfig

    lateinit var fakePackageName: String
    lateinit var fakePackageVersion: String

    @TempDir
    lateinit var rootDir: File

    @BeforeEach
    fun `set up`(forge: Forge) {
        CoreFeature.isMainProcess = true
        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )

        fakePackageName = forge.anAlphabeticalString()
        fakePackageVersion = forge.aStringMatching("\\d(\\.\\d){3}")

        mockAppContext = mockContext(fakePackageName, fakePackageVersion)
        whenever(mockAppContext.filesDir).thenReturn(rootDir)
        whenever(mockAppContext.applicationContext) doReturn mockAppContext
    }

    @AfterEach
    fun `tear down`() {
        LogsFeature.stop()
        CoreFeature.stop()
    }

    @Test
    fun `initializes persistence strategy`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        val persistenceStrategy = LogsFeature.persistenceStrategy

        assertThat(persistenceStrategy)
            .isInstanceOf(AsyncWriterFilePersistenceStrategy::class.java)
    }

    @Test
    fun `initializes uploader thread`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        val dataUploadScheduler = LogsFeature.dataUploadScheduler

        assertThat(dataUploadScheduler)
            .isInstanceOf(DataUploadScheduler::class.java)
    }

    @Test
    fun `initializes from configuration`() {
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl

        assertThat(clientToken).isEqualTo(fakeConfig.clientToken)
        assertThat(endpointUrl).isEqualTo(fakeConfig.endpointUrl)
    }

    @Test
    fun `ignores if initialize called more than once`(forge: Forge) {
        Datadog.setVerbosity(android.util.Log.VERBOSE)
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )
        val persistenceStrategy = LogsFeature.persistenceStrategy
        val dataUploadScheduler = LogsFeature.dataUploadScheduler
        val clientToken = LogsFeature.clientToken
        val endpointUrl = LogsFeature.endpointUrl

        fakeConfig = DatadogConfig.FeatureConfig(
            clientToken = forge.anHexadecimalString(),
            applicationId = forge.getForgery(),
            endpointUrl = forge.getForgery<URL>().toString(),
            envName = forge.anAlphabeticalString()
        )
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )
        val persistenceStrategy2 = LogsFeature.persistenceStrategy
        val dataUploadScheduler2 = LogsFeature.dataUploadScheduler
        val clientToken2 = LogsFeature.clientToken
        val endpointUrl2 = LogsFeature.endpointUrl

        assertThat(persistenceStrategy).isSameAs(persistenceStrategy2)
        assertThat(dataUploadScheduler).isSameAs(dataUploadScheduler2)
        assertThat(clientToken).isSameAs(clientToken2)
        assertThat(endpointUrl).isSameAs(endpointUrl2)
    }

    @Test
    fun `it will register the provided plugin when feature was initialized`(
        forge: Forge
    ) {
        // given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }

        // when
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        val argumentCaptor = argumentCaptor<DatadogPluginConfig>()
        // then
        val mockedPlugins = plugins.toTypedArray()
        inOrder(*mockedPlugins) {
            mockedPlugins.forEach {
                verify(it).register(argumentCaptor.capture())
            }
        }

        argumentCaptor.allValues.forEach {
            assertThat(it).isInstanceOf(DatadogPluginConfig.LogsPluginConfig::class.java)
            assertThat(it.context).isEqualTo(mockAppContext)
            assertThat(it.serviceName).isEqualTo(CoreFeature.serviceName)
            assertThat(it.envName).isEqualTo(fakeConfig.envName)
            assertThat(it.featurePersistenceDirName).isEqualTo(LogFileStrategy.LOGS_FOLDER)
            assertThat(it.context).isEqualTo(mockAppContext)
        }
    }

    @Test
    fun `it will unregister the provided plugin when stop called`(
        forge: Forge
    ) {
        // given
        val plugins: List<DatadogPlugin> = forge.aList(forge.anInt(min = 1, max = 10)) {
            mock<DatadogPlugin>()
        }
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig.copy(plugins = plugins),
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        // when
        LogsFeature.stop()

        // then
        val mockedPlugins = plugins.toTypedArray()
        inOrder(*mockedPlugins) {
            mockedPlugins.forEach {
                verify(it).unregister()
            }
        }
    }

    @Test
    fun `will use a NoOpUploadScheduler if this is not the application main process`() {
        // given
        CoreFeature.isMainProcess = false

        // when
        LogsFeature.initialize(
            mockAppContext,
            fakeConfig,
            mockOkHttpClient,
            mockNetworkInfoProvider,
            mockSystemInfoProvider,
            mockScheduledThreadPoolExecutor,
            mockPersistenceExecutorService
        )

        // then
        assertThat(LogsFeature.dataUploadScheduler).isInstanceOf(NoOpUploadScheduler::class.java)
    }
}
