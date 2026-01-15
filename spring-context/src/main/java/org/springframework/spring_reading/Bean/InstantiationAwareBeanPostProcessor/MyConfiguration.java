package org.springframework.spring_reading.Bean.InstantiationAwareBeanPostProcessor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyConfiguration {

	@Bean
	public static MyInstantiationAwareBeanPostProcessor myInstantiationAwareBeanPostProcessor() {
		return new MyInstantiationAwareBeanPostProcessor();
	}

	@Bean
	public MyDataBase dataBase() {
		return new MyDataBaseImpl();
	}
}
