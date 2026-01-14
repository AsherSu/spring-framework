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

package org.springframework.aop;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

/**
 * 扩展了AOP的 {@link org.aopalliance.intercept.MethodInvocation} 接口，
 * 允许访问通过方法调用所使用的代理对象。
 *
 * <p>如果需要的话，通过此接口可以方便地使用代理对象替换返回值，
 * 例如如果调用目标返回了自身对象。
 *
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @since 1.1.3
 * @see org.springframework.aop.framework.ReflectiveMethodInvocation
 * @see org.springframework.aop.support.DelegatingIntroductionInterceptor
 */
public interface ProxyMethodInvocation extends MethodInvocation {

	/**
	 * 返回执行此方法调用的代理对象。
	 * @return 原始代理对象
	 */
	Object getProxy();

	/**
	 * 创建此对象的克隆。如果在此对象上调用 {@code proceed()} 之前进行克隆，
	 * 则每个克隆可以调用 {@code proceed()} 一次，以多次调用连接点（以及其余的通知链）。
	 * @return 此调用的可调用克隆。
	 * {@code proceed()} 可以每个克隆调用一次。
	 */
	MethodInvocation invocableClone();

	/**
	 * 创建此对象的克隆，并指定克隆对象所使用的参数。如果在此对象上调用 {@code proceed()} 之前进行克隆，
	 * 则每个克隆可以调用 {@code proceed()} 一次，以多次调用连接点（以及其余的通知链）。
	 * @param arguments 克隆调用所使用的参数，覆盖原始参数
	 * @return 此调用的可调用克隆。
	 * {@code proceed()} 可以每个克隆调用一次。
	 */
	MethodInvocation invocableClone(@Nullable Object... arguments);

	/**
	 * 设置将在此链中的后续调用中使用的参数。
	 * @param arguments 参数数组
	 */
	void setArguments(@Nullable Object... arguments);

	/**
	 * 向此方法调用添加指定的用户属性和给定的值。
	 * <p>这些属性在AOP框架内部不使用。它们只是作为调用对象的一部分保留，
	 * 供特殊拦截器使用。
	 * @param key 属性的名称
	 * @param value 属性的值，如果要重置则传入 {@code null}
	 */
	void setUserAttribute(String key, @Nullable Object value);

	/**
	 * 返回指定用户属性的值。
	 * @param key 属性的名称
	 * @return 属性的值，如果未设置则返回 {@code null}
	 * @see #setUserAttribute
	 */
	@Nullable Object getUserAttribute(String key);

}
