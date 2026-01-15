package org.springframework.spring_reading.Bean.InstantiationAwareBeanPostProcessor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class InstantiationAwareBeanPostProcessorApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		MyDataBase userService = context.getBean(MyDataBase.class);
		System.out.println("username = " + userService.getUsername());
		System.out.println("password = " + userService.getPassword());
		System.out.println("postInstantiationFlag = " + userService.isPostInstantiationFlag());
	}
}