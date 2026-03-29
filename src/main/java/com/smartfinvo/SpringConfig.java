package com.smartfinvo;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

@Configuration
@EnableAutoConfiguration(exclude = { SecurityAutoConfiguration.class })
@EnableR2dbcAuditing
public class SpringConfig {}
