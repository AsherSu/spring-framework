package org.springframework.spring_reading;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.spring_reading.My.MyConfiguration;

public class GetBeanApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		System.out.println("myServiceA = " + context.getBean("myServiceA"));
		System.out.println("myServiceB = " + context.getBean("myServiceB"));
	}
}
