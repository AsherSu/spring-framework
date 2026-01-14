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

package org.springframework.aop.framework;

import java.lang.reflect.Method;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Advisor链工厂的工厂接口。
 * Factory interface for advisor chains.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface AdvisorChainFactory {

	/**
	 * 根据给定的Advisor链配置，确定一组MethodInterceptor对象。
	 * Determine a list of {@link org.aopalliance.intercept.MethodInterceptor} objects
	 * for the given advisor chain configuration.
	 * @param config 表示AOP配置的Advised对象
	 * @param method 被代理的方法
	 * @param targetClass 目标类（可能为null，表示没有目标对象的代理，在这种情况下，方法的声明类是下一个最佳选择）
	 * @return 一个MethodInterceptors的列表（也可能包括InterceptorAndDynamicMethodMatchers）
	 */
	List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass);

}
