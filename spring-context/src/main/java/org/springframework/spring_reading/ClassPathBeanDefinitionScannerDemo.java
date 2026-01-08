package org.springframework.spring_reading;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.spring_reading.My.MyController;
import org.springframework.spring_reading.My.MyRepository;
import org.springframework.spring_reading.My.MyService;

// 扫描bean

// ClassPathBeanDefinitionScanner 类，用于在类路径上 扫描指定包及其子包中 的类，识别符合条件的类，
// 并将其注册为 Spring Bean 的定义。从而实现组件扫描和自动装配，使我们能够方便地管理和配置应用程序
// 中的 Bean。它允许我们定义过滤条件，以确定哪些类应被注册为 Bean，以及配合自动装配实现依赖注入，提
// 高了应用程序的可维护性和扩展性。
public class ClassPathBeanDefinitionScannerDemo {

	public static void main(String[] args) {
		// 创建一个 AnnotationConfigApplicationContext
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		// 创建 ClassPathBeanDefinitionScanner 并将其关联到容器
		ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(factory);

		// 使用 ClassPathBeanDefinitionScanner的scan方法扫描Bean对象
		scanner.scan("org.springframework.spring_reading.My");

		System.out.println("MyController = " + factory.getBean(MyController.class));
		System.out.println("MyService = " + factory.getBean(MyService.class));
		System.out.println("MyRepository = " + factory.getBean(MyRepository.class));
	}
}
