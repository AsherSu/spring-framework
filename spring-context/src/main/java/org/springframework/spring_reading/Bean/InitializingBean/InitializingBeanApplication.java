package org.springframework.spring_reading.Bean.InitializingBean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class InitializingBeanApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
	}
}
