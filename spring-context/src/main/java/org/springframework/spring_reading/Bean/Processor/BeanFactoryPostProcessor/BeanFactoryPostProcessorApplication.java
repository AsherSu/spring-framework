package org.springframework.spring_reading.Bean.Processor.BeanFactoryPostProcessor;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class BeanFactoryPostProcessorApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);

		MySimpleBean mySimpleBean1 = context.getBean(MySimpleBean.class);
		MySimpleBean mySimpleBean2 = context.getBean(MySimpleBean.class);

		mySimpleBean1.show();
		mySimpleBean2.show();
	}
}
