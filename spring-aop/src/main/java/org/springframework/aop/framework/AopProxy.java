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

import org.jspecify.annotations.Nullable;

/**
 * 配置AOP代理的委托接口，允许创建实际的代理对象。
 *
 * <p>默认情况下，可用于创建代理对象的实现包括JDK动态代理和CGLIB代理，
 * 这些代理实现由 {@link DefaultAopProxyFactory} 应用。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see DefaultAopProxyFactory
 */
public interface AopProxy {

	/**
	 * 创建一个新的代理对象。
	 * <p>使用AopProxy的默认类加载器（必要时用于代理创建）：
	 * 通常为线程上下文类加载器。
	 * @return 新的代理对象（永远不会是 {@code null}）
	 * @see Thread#getContextClassLoader()
	 */
	Object getProxy();

	/**
	 * 创建一个新的代理对象。
	 * <p>使用给定的类加载器（必要时用于代理创建）。
	 * 如果给定的类加载器为 {@code null}，则简单地传递并因此导致低级代理工具的默认值，
	 * 这通常不同于AopProxy实现的 {@link #getProxy()} 方法选择的默认值。
	 * @param classLoader 用于创建代理的类加载器
	 * （或 {@code null} 表示使用低级代理工具的默认值）
	 * @return 新的代理对象（永远不会是 {@code null}）
	 */
	Object getProxy(@Nullable ClassLoader classLoader);

	/**
	 * 确定代理类。
	 * @param classLoader 用于创建 代理类 的类加载器
	 *（或者低级代理工具的默认值是 {@code null}）
	 * @return 代理类
	 * @since 6.0
	 */
	Class<?> getProxyClass(@Nullable ClassLoader classLoader);

}
