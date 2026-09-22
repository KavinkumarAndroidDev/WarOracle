package com.kkdev.waroracle.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class HazelcastConfig
{

	@Value("${hazelcast.cluster-name:dev}")
	private String clusterName;

	@Value("${hazelcast.network.addresses:127.0.0.1:5701}")
	private String networkAddresses;

	@Bean
	public HazelcastInstance hazelcastInstance()
	{
		log.info("Configuring Hazelcast instance. Target cluster: {}, addresses: {}", clusterName, networkAddresses);
		try
		{
			ClientConfig clientConfig = new ClientConfig();
			clientConfig.setClusterName(clusterName);
			clientConfig.getNetworkConfig().addAddress(networkAddresses.split(","));
			clientConfig.getConnectionStrategyConfig().getConnectionRetryConfig().setClusterConnectTimeoutMillis(3000);

			HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);
			log.info("Successfully connected to standalone Hazelcast cluster: {}", clusterName);
			return client;
		}
		catch (Exception ex)
		{
			log.warn("Unable to connect to standalone Hazelcast at {}. Starting local standalone Hazelcast instance for local development. Cause: {}",
					networkAddresses, ex.getMessage());

			Config config = new Config();
			config.setClusterName(clusterName);
			config.getJetConfig().setEnabled(false);
			return Hazelcast.newHazelcastInstance(config);
		}
	}
}
