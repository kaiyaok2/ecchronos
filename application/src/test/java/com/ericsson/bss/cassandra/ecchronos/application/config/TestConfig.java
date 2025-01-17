/*
 * Copyright 2020 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecchronos.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.management.remote.JMXConnector;
import javax.net.ssl.SSLEngine;

import com.datastax.driver.core.EndPoint;
import com.ericsson.bss.cassandra.ecchronos.connection.CertificateHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.Test;
import org.springframework.context.ApplicationContext;

import com.datastax.driver.core.Host;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.ericsson.bss.cassandra.ecchronos.application.*;
import com.ericsson.bss.cassandra.ecchronos.connection.JmxConnectionProvider;
import com.ericsson.bss.cassandra.ecchronos.connection.NativeConnectionProvider;
import com.ericsson.bss.cassandra.ecchronos.connection.StatementDecorator;
import com.ericsson.bss.cassandra.ecchronos.core.repair.RepairConfiguration;
import com.ericsson.bss.cassandra.ecchronos.core.repair.RepairLockType;
import com.ericsson.bss.cassandra.ecchronos.core.repair.RepairOptions;
import com.ericsson.bss.cassandra.ecchronos.core.utils.TableReference;
import com.ericsson.bss.cassandra.ecchronos.core.utils.UnitConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class TestConfig
{
    @Test
    public void testAllValues() throws Exception
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("all_set.yml").getFile());

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        Config config = objectMapper.readValue(file, Config.class);

        Config.ConnectionConfig connection = config.getConnectionConfig();

        Config.NativeConnection nativeConnection = connection.getCql();
        assertThat(nativeConnection.getHost()).isEqualTo("127.0.0.2");
        assertThat(nativeConnection.getPort()).isEqualTo(9100);
        assertThat(nativeConnection.getRemoteRouting()).isFalse();
        assertThat(nativeConnection.getTimeout().getConnectionTimeout(TimeUnit.SECONDS)).isEqualTo(5);
        assertThat(nativeConnection.getProviderClass()).isEqualTo(TestNativeConnectionProvider.class);
        assertThat(nativeConnection.getCertificateHandlerClass()).isEqualTo(TestCertificateHandler.class);
        assertThat(nativeConnection.getDecoratorClass()).isEqualTo(TestStatementDecorator.class);

        Config.Connection jmxConnection = connection.getJmx();
        assertThat(jmxConnection.getHost()).isEqualTo("127.0.0.3");
        assertThat(jmxConnection.getPort()).isEqualTo(7100);
        assertThat(jmxConnection.getProviderClass()).isEqualTo(TestJmxConnectionProvider.class);

        RepairConfiguration expectedConfiguration = RepairConfiguration.newBuilder()
                .withRepairInterval(24, TimeUnit.HOURS)
                .withParallelism(RepairOptions.RepairParallelism.PARALLEL)
                .withRepairWarningTime(48, TimeUnit.HOURS)
                .withRepairErrorTime(72, TimeUnit.HOURS)
                .withRepairUnwindRatio(0.5d)
                .withTargetRepairSizeInBytes(UnitConverter.toBytes("5m"))
                .build();

        Config.GlobalRepairConfig repairConfig = config.getRepair();
        assertThat(repairConfig.asRepairConfiguration()).isEqualTo(expectedConfiguration);

        assertThat(repairConfig.getLockType()).isEqualTo(RepairLockType.DATACENTER);
        assertThat(repairConfig.getProvider()).isEqualTo(TestRepairConfigurationProvider.class);

        assertThat(repairConfig.getHistoryLookback().getInterval(TimeUnit.DAYS)).isEqualTo(13);
        assertThat(repairConfig.getHistory().getProvider()).isEqualTo(Config.RepairHistory.Provider.CASSANDRA);
        assertThat(repairConfig.getHistory().getKeyspace()).isEqualTo("customkeyspace");

        Config.StatisticsConfig statisticsConfig = config.getStatistics();
        assertThat(statisticsConfig.isEnabled()).isFalse();
        assertThat(statisticsConfig.getDirectory()).isEqualTo(new File("./non-default-statistics"));

        Config.LockFactoryConfig lockFactoryConfig = config.getLockFactory();
        assertThat(lockFactoryConfig.getCas().getKeyspace()).isEqualTo("ecc");

        Config.RunPolicyConfig runPolicyConfig = config.getRunPolicy();
        assertThat(runPolicyConfig.getTimeBased().getKeyspace()).isEqualTo("ecc");

        Config.SchedulerConfig schedulerConfig = config.getScheduler();
        assertThat(schedulerConfig.getFrequency().getInterval(TimeUnit.SECONDS)).isEqualTo(60);

        Config.RestServerConfig restServerConfig = config.getRestServer();
        assertThat(restServerConfig.getHost()).isEqualTo("127.0.0.2");
        assertThat(restServerConfig.getPort()).isEqualTo(8081);
    }

    @Test
    public void testWithDefaultFile() throws Exception
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("ecc.yml").getFile());

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        Config config = objectMapper.readValue(file, Config.class);

        Config.ConnectionConfig connection = config.getConnectionConfig();

        Config.NativeConnection nativeConnection = connection.getCql();
        assertThat(nativeConnection.getHost()).isEqualTo("localhost");
        assertThat(nativeConnection.getPort()).isEqualTo(9042);
        assertThat(nativeConnection.getRemoteRouting()).isTrue();
        assertThat(nativeConnection.getTimeout().getConnectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(0);
        assertThat(nativeConnection.getProviderClass()).isEqualTo(DefaultNativeConnectionProvider.class);
        assertThat(nativeConnection.getCertificateHandlerClass()).isEqualTo(ReloadingCertificateHandler.class);
        assertThat(nativeConnection.getDecoratorClass()).isEqualTo(NoopStatementDecorator.class);

        Config.Connection jmxConnection = connection.getJmx();
        assertThat(jmxConnection.getHost()).isEqualTo("localhost");
        assertThat(jmxConnection.getPort()).isEqualTo(7199);
        assertThat(jmxConnection.getProviderClass()).isEqualTo(DefaultJmxConnectionProvider.class);

        RepairConfiguration expectedConfiguration = RepairConfiguration.newBuilder()
                .withRepairInterval(7, TimeUnit.DAYS)
                .withParallelism(RepairOptions.RepairParallelism.PARALLEL)
                .withRepairWarningTime(8, TimeUnit.DAYS)
                .withRepairErrorTime(10, TimeUnit.DAYS)
                .withRepairUnwindRatio(0.0d)
                .withTargetRepairSizeInBytes(RepairConfiguration.FULL_REPAIR_SIZE)
                .build();

        Config.GlobalRepairConfig repairConfig = config.getRepair();

        assertThat(repairConfig.asRepairConfiguration()).isEqualTo(expectedConfiguration);

        assertThat(repairConfig.getLockType()).isEqualTo(RepairLockType.VNODE);
        assertThat(repairConfig.getProvider()).isEqualTo(FileBasedRepairConfiguration.class);

        assertThat(repairConfig.getHistoryLookback().getInterval(TimeUnit.DAYS)).isEqualTo(30);
        assertThat(repairConfig.getHistory().getProvider()).isEqualTo(Config.RepairHistory.Provider.ECC);
        assertThat(repairConfig.getHistory().getKeyspace()).isEqualTo("ecchronos");

        Config.StatisticsConfig statisticsConfig = config.getStatistics();
        assertThat(statisticsConfig.isEnabled()).isTrue();
        assertThat(statisticsConfig.getDirectory()).isEqualTo(new File("./statistics"));

        Config.LockFactoryConfig lockFactoryConfig = config.getLockFactory();
        assertThat(lockFactoryConfig.getCas().getKeyspace()).isEqualTo("ecchronos");

        Config.RunPolicyConfig runPolicyConfig = config.getRunPolicy();
        assertThat(runPolicyConfig.getTimeBased().getKeyspace()).isEqualTo("ecchronos");

        Config.SchedulerConfig schedulerConfig = config.getScheduler();
        assertThat(schedulerConfig.getFrequency().getInterval(TimeUnit.SECONDS)).isEqualTo(30);

        Config.RestServerConfig restServerConfig = config.getRestServer();
        assertThat(restServerConfig.getHost()).isEqualTo("localhost");
        assertThat(restServerConfig.getPort()).isEqualTo(8080);
    }

    @Test
    public void testDefault() throws Exception
    {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        File file = new File(classLoader.getResource("nothing_set.yml").getFile());

        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        Config config = objectMapper.readValue(file, Config.class);

        Config.ConnectionConfig connection = config.getConnectionConfig();

        Config.NativeConnection nativeConnection = connection.getCql();
        assertThat(nativeConnection.getHost()).isEqualTo("localhost");
        assertThat(nativeConnection.getPort()).isEqualTo(9042);
        assertThat(nativeConnection.getRemoteRouting()).isTrue();
        assertThat(nativeConnection.getTimeout().getConnectionTimeout(TimeUnit.MILLISECONDS)).isEqualTo(0);
        assertThat(nativeConnection.getProviderClass()).isEqualTo(DefaultNativeConnectionProvider.class);
        assertThat(nativeConnection.getCertificateHandlerClass()).isEqualTo(ReloadingCertificateHandler.class);
        assertThat(nativeConnection.getDecoratorClass()).isEqualTo(NoopStatementDecorator.class);

        Config.Connection jmxConnection = connection.getJmx();
        assertThat(jmxConnection.getHost()).isEqualTo("localhost");
        assertThat(jmxConnection.getPort()).isEqualTo(7199);
        assertThat(jmxConnection.getProviderClass()).isEqualTo(DefaultJmxConnectionProvider.class);

        RepairConfiguration expectedConfiguration = RepairConfiguration.newBuilder()
                .withRepairInterval(7, TimeUnit.DAYS)
                .withParallelism(RepairOptions.RepairParallelism.PARALLEL)
                .withRepairWarningTime(8, TimeUnit.DAYS)
                .withRepairErrorTime(10, TimeUnit.DAYS)
                .withRepairUnwindRatio(0.0d)
                .withTargetRepairSizeInBytes(RepairConfiguration.FULL_REPAIR_SIZE)
                .build();

        Config.GlobalRepairConfig repairConfig = config.getRepair();

        assertThat(repairConfig.asRepairConfiguration()).isEqualTo(expectedConfiguration);

        assertThat(repairConfig.getLockType()).isEqualTo(RepairLockType.VNODE);
        assertThat(repairConfig.getProvider()).isEqualTo(FileBasedRepairConfiguration.class);

        assertThat(repairConfig.getHistoryLookback().getInterval(TimeUnit.DAYS)).isEqualTo(30);
        assertThat(repairConfig.getHistory().getProvider()).isEqualTo(Config.RepairHistory.Provider.ECC);
        assertThat(repairConfig.getHistory().getKeyspace()).isEqualTo("ecchronos");

        Config.StatisticsConfig statisticsConfig = config.getStatistics();
        assertThat(statisticsConfig.isEnabled()).isTrue();
        assertThat(statisticsConfig.getDirectory()).isEqualTo(new File("./statistics"));

        Config.LockFactoryConfig lockFactoryConfig = config.getLockFactory();
        assertThat(lockFactoryConfig.getCas().getKeyspace()).isEqualTo("ecchronos");

        Config.RunPolicyConfig runPolicyConfig = config.getRunPolicy();
        assertThat(runPolicyConfig.getTimeBased().getKeyspace()).isEqualTo("ecchronos");

        Config.SchedulerConfig schedulerConfig = config.getScheduler();
        assertThat(schedulerConfig.getFrequency().getInterval(TimeUnit.SECONDS)).isEqualTo(30);

        Config.RestServerConfig restServerConfig = config.getRestServer();
        assertThat(restServerConfig.getHost()).isEqualTo("localhost");
        assertThat(restServerConfig.getPort()).isEqualTo(8080);
    }

    public static class TestNativeConnectionProvider implements NativeConnectionProvider
    {
        public TestNativeConnectionProvider(Config config, Supplier<Security.CqlSecurity> cqlSecurity)
        {
            // Empty constructor
        }

        @Override
        public Session getSession()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Host getLocalHost()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean getRemoteRouting()
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestCertificateHandler implements CertificateHandler
    {
        public TestCertificateHandler(Supplier<TLSConfig> tlsConfigSupplier)
        {
            // Empty constructor
        }
        @Override
        public SslHandler newSSLHandler(SocketChannel channel, EndPoint remoteEndpoint)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SSLEngine newSSLEngine(EndPoint remoteEndpoint)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SslHandler newSSLHandler(SocketChannel channel, InetSocketAddress remoteEndpoint)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public SslHandler newSSLHandler(SocketChannel channel)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestJmxConnectionProvider implements JmxConnectionProvider
    {
        public TestJmxConnectionProvider(Config config, Supplier<Security.JmxSecurity> jmxSecurity)
        {
            // Empty constructor
        }

        @Override
        public JMXConnector getJmxConnector()
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestStatementDecorator implements StatementDecorator
    {
        public TestStatementDecorator(Config config)
        {
            // Empty constructor
        }

        @Override
        public Statement apply(Statement statement)
        {
            throw new UnsupportedOperationException();
        }
    }

    public static class TestRepairConfigurationProvider extends AbstractRepairConfigurationProvider
    {
        protected TestRepairConfigurationProvider(ApplicationContext applicationContext)
        {
            super(applicationContext);
        }

        @Override
        public Optional<RepairConfiguration> forTable(TableReference tableReference)
        {
            return Optional.empty();
        }
    }
}
