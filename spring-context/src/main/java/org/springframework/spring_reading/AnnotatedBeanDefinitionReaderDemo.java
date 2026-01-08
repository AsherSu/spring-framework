package org.springframework.spring_reading;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.spring_reading.My.MyBean;

// 获取注解bean

// AnnotatedBeanDefinitionReader是一个用于 读取 和 解析 带有注解的Bean定义的类，它主要用于基于注解的配置方式，
// 允许开发者将Java类标记为Spring组件，从而让Spring容器自动扫描和注册这些组件，而不需要显式配置这些组件的Bean定义。
public class AnnotatedBeanDefinitionReaderDemo {
	public static void main(String[] args) {
		// 创建一个 AnnotationConfigApplicationContext
		DefaultListableBeanFactory factory = new DefaultListableBeanFactory();

		// 创建 AnnotatedBeanDefinitionReader 并将其关联到容器
		AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(factory);

		// 使用 AnnotatedBeanDefinitionReader 注册Bean对象
		reader.registerBean(MyBean.class);

		// 获取并打印 MyBean
		System.out.println("MyBean = " + factory.getBean(MyBean.class));
	}
}
