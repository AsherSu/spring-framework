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

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;

/**
 * 工厂钩子，允许对新创建的bean实例进行自定义修改。
 * 例如，检查标记接口或使用代理包装beans。
 *
 * 通常，通过标记接口等填充beans的后处理器会实现 #postProcessBeforeInitialization
 * 而使用代理包装beans的后处理器通常会实现 #postProcessAfterInitialization
 *
 * 注册
 * ApplicationContext 可以在其bean定义中自动检测到 BeanPostProcessor beans，
 * 并将这些后处理器应用于随后创建的任何beans。一个普通的 BeanFactory 允许以编程方式注册
 * 后处理器，将它们应用于通过bean工厂创建的所有beans。
 *
 * 排序
 * 在 ApplicationContext 中自动检测到的 BeanPostProcessor beans 将根据
 * org.springframework.core.PriorityOrdered 和
 * org.springframework.core.Ordered 语义进行排序。相反，以编程方式在
 * BeanFactory 中注册的 BeanPostProcessor beans 将按注册顺序应用；
 * 通过实现 PriorityOrdered 或 Ordered 接口表达的任何排序语义
 * 对于编程注册的后处理器都将被忽略。此外，org.springframework.core.annotation.Order @Order
 * 注释不适用于 BeanPostProcessor beans。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 10.10.2003
 * @see InstantiationAwareBeanPostProcessor
 * @see DestructionAwareBeanPostProcessor
 * @see ConfigurableBeanFactory#addBeanPostProcessor
 * @see BeanFactoryPostProcessor
 */
public interface BeanPostProcessor {

	/**
	 * 在任何bean初始化回调（如InitializingBean的 afterPropertiesSet
	 * 或自定义初始化方法）之前，将此 BeanPostProcessor 应用于给定的新bean实例。
	 * 该bean已使用属性值填充。返回的bean实例可能是原始实例的包装。
	 * 默认实现返回给定的 bean。
	 * @param bean 新的bean实例
	 * @param beanName bean的名称
	 * @return 要使用的bean实例，可以是原始实例或其包装；如果为 null，则不会调用后续的BeanPostProcessors
	 * @throws org.springframework.beans.BeansException 出错时
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 */
	@Nullable
	default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * 在任何bean初始化回调（如InitializingBean的 afterPropertiesSet
	 * 或自定义初始化方法）之后，将此 BeanPostProcessor 应用于给定的新bean实例。
	 * 该bean已使用属性值填充。返回的bean实例可能是原始实例的包装。
	 * 对于FactoryBean，此回调将被调用，既适用于FactoryBean实例，也适用于FactoryBean创建的对象（自Spring 2.0起）。
	 * 后处理器可以决定通过相应的 bean instanceof FactoryBean 检查，是否应用于FactoryBean、创建的对象或两者。
	 * 此回调还将在由 InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 * 方法触发的短路之后调用，与所有其他 BeanPostProcessor 回调相反。
	 * 默认实现返回给定的 bean。
	 * @param bean 新的bean实例
	 * @param beanName bean的名称
	 * @return 要使用的bean实例，可以是原始实例或其包装；如果为 null，则不会调用后续的BeanPostProcessors
	 * @throws org.springframework.beans.BeansException 出错时
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.FactoryBean
	 */
	@Nullable
	default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
