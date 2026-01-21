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

import org.springframework.beans.BeansException;

/**
 * BeanFactoryPostProcessor 是一个核心接口，允许用户在Spring容器初始化bean之前修改bd。
 * 它提供了一个强大的方式来修改bean的属性和依赖关系，使得我们可以根据特定的配置或环境条件动态地调整这些值。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 06.07.2003
 * @see BeanPostProcessor
 * @see PropertyResourceConfigurer
 */
@FunctionalInterface
public interface BeanFactoryPostProcessor {

	/**
	 * 在应用上下文的内部bean工厂进行其标准初始化后修改它。
	 * 此时，所有bean定义都已加载，但尚未实例化任何bean。
	 * 这允许用户即使对于急切初始化的beans也可以覆盖或添加属性。
	 *
	 * @param beanFactory 应用上下文使用的bean工厂
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
