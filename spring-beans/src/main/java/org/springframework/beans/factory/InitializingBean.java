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

package org.springframework.beans.factory;

/**
 * 接口定义，用于需要在其所有属性被 BeanFactory 设置后执行操作的 beans。
 * 例如，可以执行自定义初始化或检查所有必需属性是否已设置。
 *
 * 实现此接口的 beans 会在所有属性都设置完毕后，由 BeanFactory 调用其 `afterPropertiesSet()` 方法。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DisposableBean  // 当 bean 不再需要时，用于回调的接口
 * @see org.springframework.beans.factory.config.BeanDefinition#getPropertyValues()
 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getInitMethodName()
 */
public interface InitializingBean {

	/**
	 * 当 BeanFactory 设置了 bean 的所有属性后调用此方法。
	 * 也即满足了 BeanFactoryAware, ApplicationContextAware 等条件后。
	 *
	 * 此方法让 bean 实例可以在所有属性都设置后进行最终的配置验证和初始化。
	 * 如果出现配置错误（如未设置必需的属性）或因其他原因初始化失败，此方法可能会抛出异常。
	 *
	 * @throws Exception 配置错误或其他任何初始化失败原因导致的异常
	 */
	void afterPropertiesSet() throws Exception;
}
