package org.springframework.spring_reading.Bean.Processor.BeanFactoryPostProcessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
		System.out.println("修改bean的定义");
		BeanDefinition beanDefinition = beanFactory.getBeanDefinition("mySimpleBean");
		beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		System.out.println("将mySimpleBean从默认的单例修改成多例");
		System.out.println("修改bean的定义已完成");
	}
}
