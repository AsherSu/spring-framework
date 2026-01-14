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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	// 用于存储注册的AdvisorAdapter的列表
	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * 创建一个新的 DefaultAdvisorAdapterRegistry 实例，并注册已知的适配器。
	 * 这里的“已知的适配器”包括MethodBeforeAdviceAdapter、AfterReturningAdviceAdapter、ThrowsAdviceAdapter。
	 */
	public DefaultAdvisorAdapterRegistry() {
		// 注册MethodBeforeAdviceAdapter适配器
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		// 注册AfterReturningAdviceAdapter适配器
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		// 注册ThrowsAdviceAdapter适配器
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}

	/**
	 * 将给定的 adviceObject 包装为 Advisor。
	 * 如果 adviceObject 已经是 Advisor，则直接返回；
	 * 如果不是 Advice 类型，则抛出 UnknownAdviceTypeException；
	 * 如果 advice 是 MethodInterceptor 类型，则创建一个 DefaultPointcutAdvisor 并返回；
	 * 否则，遍历已注册的 AdvisorAdapter ，找到支持 advice 的适配器，创建一个 DefaultPointcutAdvisor 并返回。
	 *
	 * @param adviceObject 要包装为Advisor的Advice对象
	 * @return 包装后的Advisor对象
	 * @throws UnknownAdviceTypeException 如果adviceObject无法被识别为Advisor或Advice
	 */
	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		// 如果 adviceObject 已经是 Advisor，则直接返回；
		if (adviceObject instanceof Advisor advisor) {
			return advisor;
		}
		// 如果不是 Advice 类型，则抛出 UnknownAdviceTypeException
		if (!(adviceObject instanceof Advice advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		// 如果 advice 是 MethodInterceptor 类型，则创建一个 DefaultPointcutAdvisor 并返回
		if (advice instanceof MethodInterceptor) {
			// 对于MethodInterceptor类型的Advice，不需要适配器，直接创建Advisor并返回
			return new DefaultPointcutAdvisor(advice);
		}
		// 遍历已注册的AdvisorAdapter，查找支持当前Advice的适配器
		for (AdvisorAdapter adapter : this.adapters) {
			// 检查是否支持当前Advice
			if (adapter.supportsAdvice(advice)) {
				// 创建Advisor并返回
				return new DefaultPointcutAdvisor(advice);
			}
		}
		// 如果无法找到合适的适配器，抛出异常
		throw new UnknownAdviceTypeException(advice);
	}

	@Override
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		Advice advice = advisor.getAdvice();
		if (advice instanceof MethodInterceptor methodInterceptor) {
			interceptors.add(methodInterceptor);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	/**
	 * 注册一个Advisor适配器。
	 * @param adapter 要注册的Advisor适配器
	 */
	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
