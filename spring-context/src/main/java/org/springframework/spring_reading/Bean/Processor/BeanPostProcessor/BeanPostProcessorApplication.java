package org.springframework.spring_reading.Bean.Processor.BeanPostProcessor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanPostProcessorApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		MyService myService = context.getBean(MyService.class);
		System.out.println(myService.show());
		context.close();
	}
}
