package org.springframework.spring_reading.Bean.InitializingBean;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {

	@Bean
	public static MyInitializingBean myInitializingBean(){
		return new MyInitializingBean();
	}
}
