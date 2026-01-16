package org.springframework.spring_reading.Bean.ResolveDependency;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ComponentScan("org.springframework.spring_reading.Bean.ResolveDependency")
@PropertySource("classpath:spring_reading/application.properties")
public class MyConfiguration {

}
