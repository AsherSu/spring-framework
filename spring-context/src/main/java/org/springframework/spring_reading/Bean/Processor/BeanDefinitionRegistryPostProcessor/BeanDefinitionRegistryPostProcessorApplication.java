package org.springframework.spring_reading.Bean.Processor.BeanDefinitionRegistryPostProcessor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanDefinitionRegistryPostProcessorApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		MySimpleBean mySimpleBean1 = context.getBean(MySimpleBean.class);
		mySimpleBean1.show();
	}
}