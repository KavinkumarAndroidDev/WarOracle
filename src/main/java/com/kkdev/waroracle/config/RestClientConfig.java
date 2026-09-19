package com.kkdev.waroracle.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig
{
	@Bean
	public RestClient.Builder restClientBuilder()
	{
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(20));
		requestFactory.setReadTimeout(Duration.ofSeconds(30));
		return RestClient.builder().requestFactory(requestFactory);
	}
}