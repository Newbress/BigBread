package com.daeppang.core;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackageClasses = CoreConfig.class)
@EnableJpaRepositories(basePackageClasses = CoreConfig.class)
public class CoreConfig {
}
