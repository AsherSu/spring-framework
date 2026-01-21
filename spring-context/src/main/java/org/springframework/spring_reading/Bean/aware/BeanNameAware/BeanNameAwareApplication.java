package org.springframework.spring_reading.Bean.aware.BeanNameAware;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanNameAwareApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
	}
}
