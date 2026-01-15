package org.springframework.spring_reading.Bean;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.spring_reading.My.MyBean;

//综合注解bean和扫描bean

public class AnnotationConfigApplicationContextDemo {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		// 注册Bean
		context.register(MyBean.class);
		// 扫描包
		context.scan("org.springframework.spring_reading.My");
		// 打印Bean定义
		for (String beanDefinitionName : context.getBeanDefinitionNames()) {
			System.out.println("beanDefinitionName = " + beanDefinitionName);
		}
	}
}
