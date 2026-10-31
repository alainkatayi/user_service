package com.mywallet.user.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	public BCryptPasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-cost}") int bcryptCost) {
		return new BCryptPasswordEncoder(bcryptCost);
	}

}
