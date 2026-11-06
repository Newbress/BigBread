package com.bigbread.api.review.photo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PhotoProperties.class, S3Properties.class})
public class PhotoConfig {
}
