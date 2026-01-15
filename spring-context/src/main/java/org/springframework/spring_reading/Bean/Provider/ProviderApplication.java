package org.springframework.spring_reading.Bean.Provider;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class ProviderApplication {

	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MyConfiguration.class);
		MyController controller = context.getBean(MyController.class);
		controller.showService();
	}
}
