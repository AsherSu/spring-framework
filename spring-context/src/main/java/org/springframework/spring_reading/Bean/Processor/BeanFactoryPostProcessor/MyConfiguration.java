package org.springframework.spring_reading.Bean.Processor.BeanFactoryPostProcessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {

	@Bean
	public MySimpleBean mySimpleBean(){
		return new MySimpleBean();
	}

	@Bean
	public static MyBeanFactoryPostProcessor myBeanFactoryPostProcessor(){
		return new MyBeanFactoryPostProcessor();
	}
}
