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
import org.springframework.beans.PropertyValues;

// 对象的创建前后（即 new 对象的那一刻）以及属性注入阶段。

/**
 * 这是 BeanPostProcessor 的子接口，它为 bean 的实例化添加了新的回调方法。
 * 主要是在 bean 实例化之前和之后，但在明确地设置属性或进行自动装配之前。
 *
 * 通常，这个接口用于为特定的目标 beans 抑制默认的实例化。
 * 例如，为了创建带有特殊 `TargetSources` 的代理（如池化的目标、延迟初始化的目标等），
 * 或为了实施其他的注入策略，例如字段注入。
 *
 * 注意：这是一个特殊目的的接口，主要供框架内部使用。
 * 建议尽量实现简单的 BeanPostProcessor 接口，
 * 或从 InstantiationAwareBeanPostProcessorAdapter 继承，
 * 以避免受到这个接口的扩展的影响。
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @since 1.2
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#setCustomTargetSourceCreators
 * @see org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

	/**
	 * 在目标 bean 被实例化之前应用此 BeanPostProcessor。返回的 bean 对象可能是一个代理，
	 * 可用来代替目标 bean，有效地抑制了目标 bean 的默认实例化。
	 * 如果此方法返回一个非空对象，bean 的创建过程将被短路。
	 *
	 * @param beanClass 要实例化的 bean 的类
	 * @param beanName bean 的名称
	 * @return 要替代目标 bean 的默认实例公开的 bean 对象，或 {@code null} 继续默认实例化
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	default @Nullable Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * 在 bean 通过构造函数或工厂方法被实例化后执行操作，但在 Spring 的属性设置（通过明确的属性或自动装配）发生之前。
	 * 这是在 Spring 的自动装配开始之前，对给定的 bean 实例执行自定义字段注入的理想回调。
	 *
	 * @param bean 已创建的 bean 实例，其属性尚未设置
	 * @param beanName bean 的名称
	 * @return 如果应该在 bean 上设置属性，则为 {@code true}；如果应跳过属性填充，则为 {@code false}。
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		return true;
	}

	/**
	 * 在工厂将它们应用于给定的 bean 之前，对给定的属性值进行后处理，而不需要属性描述符。
	 *
	 * @param pvs 工厂即将应用的属性值（永远不为 {@code null}）
	 * @param bean 已创建但其属性尚未设置的 bean 实例
	 * @param beanName bean 的名称
	 * @return 要应用于给定 bean 的实际属性值（可以是传入的 PropertyValues 实例），或 {@code null}
	 * @throws org.springframework.beans.BeansException 如果发生错误
	 */
	default @Nullable PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
			throws BeansException {

		return pvs;
	}

}
