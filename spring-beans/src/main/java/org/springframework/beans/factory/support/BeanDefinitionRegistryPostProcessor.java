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

package org.springframework.beans.factory.support;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 相对于标准的 {@link BeanFactoryPostProcessor} SPI 的扩展，
 * 允许在常规 BeanFactoryPostProcessor 检测启动之前 注册更多的 bean 定义。
 * 特别地，BeanDefinitionRegistryPostProcessor 可以注册进一步的 bean 定义，
 * 这些定义可能会进一步定义 BeanFactoryPostProcessor 实例。
 *
 * 作者：Juergen Hoeller
 * 自版本：3.0.1 起
 * 参见：org.springframework.context.annotation.ConfigurationClassPostProcessor
 */
public interface BeanDefinitionRegistryPostProcessor extends BeanFactoryPostProcessor {

	/**
	 * 在其标准初始化之后，修改应用上下文的内部 bean 定义注册表。
	 * 此时，所有常规的 bean 定义都已经被加载，但还没有 bean 被实例化。
	 * 这允许在下一后处理阶段开始之前，添加更多的 bean 定义。
	 *
	 * @param registry 应用上下文使用的 bean 定义注册表
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException;

	/**
	 * 在应用上下文的内部bean工厂进行其标准初始化后修改它。
	 * 此时，所有bean定义都已加载，但尚未实例化任何bean。
	 * 这允许用户即使对于急切初始化的beans也可以覆盖或添加属性。
	 *
	 * @param beanFactory 应用上下文使用的bean工厂
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	@Override
	default void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
	}

}
