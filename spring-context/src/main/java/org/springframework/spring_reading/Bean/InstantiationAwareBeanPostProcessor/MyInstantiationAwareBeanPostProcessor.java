package org.springframework.spring_reading.Bean.InstantiationAwareBeanPostProcessor;

import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;

public class MyInstantiationAwareBeanPostProcessor implements InstantiationAwareBeanPostProcessor {

	@Override
	public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		if (beanClass == MyDataBase.class) {
			System.out.println("正在准备实例化: " + beanName);
		}
		return null;
	}

	@Override
	public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		if (bean instanceof MyDataBase) {
			((MyDataBase) bean).setPostInstantiationFlag(true);
			System.out.println("Bean " + beanName + " 已实例化!");
			return true;
		}
		return true;
	}

	@Override
	public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) throws BeansException {
		if (bean instanceof MyDataBase) {
			MutablePropertyValues mpvs = (MutablePropertyValues) pvs;
			mpvs.addPropertyValue("password", "******");
			System.out.println(beanName + "的密码已被屏蔽:");
		}
		return pvs;
	}
}
