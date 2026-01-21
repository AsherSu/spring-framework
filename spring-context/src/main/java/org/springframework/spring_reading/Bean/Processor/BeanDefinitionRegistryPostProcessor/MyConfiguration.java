package org.springframework.spring_reading.Bean.Processor.BeanDefinitionRegistryPostProcessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {

	@Bean
	public static MyBeanDefinitionRegistryPostProcessor myBeanDefinitionRegistryPostProcessor(){
		return new MyBeanDefinitionRegistryPostProcessor();
	}
}
