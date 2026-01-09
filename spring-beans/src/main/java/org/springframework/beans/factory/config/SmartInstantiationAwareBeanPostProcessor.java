/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.config;

import java.lang.reflect.Constructor;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;

// 实例化过程中的特殊决策点

/**
 * InstantiationAwareBeanPostProcessor 接口的扩展，
 * 增加了预测处理的bean的最终类型的回调方法。
 *
 * 注意: 这是一个特定目的的接口，主要用于
 * 框架内部。一般来说，应用程序提供的后处理器应该
 * 直接实现简单的 BeanPostProcessor
 * 接口或继承 InstantiationAwareBeanPostProcessorAdapter 类。
 * 即使在点版本中，也可能向此接口添加新方法。
 *
 * @author Juergen Hoeller
 * @since 2.0.3
 * @see InstantiationAwareBeanPostProcessorAdapter
 */
public interface SmartInstantiationAwareBeanPostProcessor extends InstantiationAwareBeanPostProcessor {

	/**
	 * 预测从此处理器的 #postProcessBeforeInstantiation 回调返回的bean的类型。
	 * 默认实现返回 null。
	 * @param beanClass bean的原始类
	 * @param beanName bean的名称
	 * @return bean的类型，如果不可预测则为 null
	 * @throws org.springframework.beans.BeansException 出错时抛出
	 */
	default @Nullable Class<?> predictBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * 确定给定bean的候选构造函数。
	 * 默认实现返回 null。
	 * @param beanClass bean的原始类（永远不是 null）
	 * @param beanName bean的名称
	 * @return 候选构造函数，如果没有指定则为 null
	 * @throws org.springframework.beans.BeansException 出错时抛出
	 */
	default Class<?> determineBeanType(Class<?> beanClass, String beanName) throws BeansException {
		return beanClass;
	}

	/**
	 * Determine the candidate constructors to use for the given bean.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the raw class of the bean (never {@code null})
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	default Constructor<?> @Nullable [] determineCandidateConstructors(Class<?> beanClass, String beanName)
			throws BeansException {

		return null;
	}

	/**
	 * 为了解决循环引用，提前获取指定bean的引用。
	 * 此回调为后处理器提供了一个机会，可以在目标bean实例完全初始化之前暴露一个包装器。
	 * 暴露的对象应当等同于 #postProcessBeforeInitialization /
	 * #postProcessAfterInitialization 否则会暴露。需要注意的是，
	 * 由此方法返回的对象将被用作bean引用，除非后处理器从上述后处理回调中返回一个不同的包装器。
	 * 默认实现返回给定的 bean 原样。
	 * @param bean 原始bean实例
	 * @param beanName bean的名称
	 * @return 作为bean引用暴露的对象（通常使用传入的bean实例作为默认值）
	 * @throws org.springframework.beans.BeansException 出错时抛出
	 */
	default Object getEarlyBeanReference(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
