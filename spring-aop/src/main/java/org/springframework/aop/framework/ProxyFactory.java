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

import org.aopalliance.intercept.Interceptor;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.TargetSource;
import org.springframework.util.ClassUtils;

/**
 * [通俗定义]：这是一个“代理工厂”。
 * 它的作用是：让你在代码里手动通过 new 的方式创建一个 AOP 代理对象，
 * 而不是非得依靠 Spring 容器的 XML 配置或注解（@Transactional 等）。
 *
 * 它是 Spring AOP 对外暴露的最常用的“制造机”。
 * * 继承关系：它继承了 ProxyCreatorSupport，这意味着它天生就拥有管理“拦截器链”、“TargetSource”的能力。
 */
@SuppressWarnings("serial")
public class ProxyFactory extends ProxyCreatorSupport {

	/**
	 * [构造器 1]：空构造器。
	 * 你买了个空锅。接下来你需要手动调用 setTarget() 和 addAdvice() 来配置它。
	 */
	public ProxyFactory() {
	}

	/**
	 * [构造器 2 - 最常用]：傻瓜式构造器。
	 * 你给它一个普通对象（Target），它会自动做两件事：
	 * 1. 把它包装成一个 SingletonTargetSource（单例源）。
	 * 2. 自动扫描这个对象实现了哪些接口，并把这些接口全部添加到代理配置中。
	 * * 结果：生成的代理会尽可能是 JDK 动态代理（因为有接口）。
	 * @param target 被代理的原始对象
	 */
	public ProxyFactory(Object target) {
		setTarget(target);
		setInterfaces(ClassUtils.getAllInterfaces(target));
	}

	/**
	 * [构造器 3]：没有目标对象，只有接口。
	 * 这种通常用于“纯拦截”场景（比如 Mock 框架，或者类似 Retrofit 那种只有接口没有实现的场景）。
	 */
	public ProxyFactory(Class<?>... proxyInterfaces) {
		setInterfaces(proxyInterfaces);
	}

	/**
	 * [构造器 4]：针对特定接口和拦截器的快捷方式。
	 * 适用于：你只想拦截某个接口的方法，而且逻辑都在拦截器里，不需要后面有个真实对象干活。
	 */
	public ProxyFactory(Class<?> proxyInterface, Interceptor interceptor) {
		addInterface(proxyInterface);
		addAdvice(interceptor);
	}

	/**
	 * [构造器 5 - 高级]：自定义 TargetSource。
	 * 如果你想用对象池、热替换等高级功能，就传 TargetSource 进去，而不是传 Object。
	 */
	public ProxyFactory(Class<?> proxyInterface, TargetSource targetSource) {
		addInterface(proxyInterface);
		setTargetSource(targetSource);
	}


	/**
	 * [核心方法]：开工制造！
	 * * 它的内部逻辑是：
	 * 1. createAopProxy()：判断是用 JDK 动态代理还是 CGLIB。
	 * - 如果有接口且没强制用 CGLIB -> JDK
	 * - 如果没接口或强制用 CGLIB -> CGLIB
	 * 2. getProxy()：利用选定的技术生成字节码或对象。
	 * * @return 最终的代理对象（Proxy）
	 */
	public Object getProxy() {
		return createAopProxy().getProxy();
	}

	/**
	 * [核心方法]：指定类加载器的制造。
	 * 有时候在复杂的 Web 容器或模块化环境（OSGi）里，你需要指定 ClassLoader 才能正确生成类。
	 */
	public Object getProxy(@Nullable ClassLoader classLoader) {
		return createAopProxy().getProxy(classLoader);
	}

	/**
	 * Determine the proxy class according to the settings in this factory.
	 * @param classLoader the class loader to create the proxy class with
	 * (or {@code null} for the low-level proxy facility's default)
	 * @return the proxy class
	 * @since 6.0
	 */
	public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
		return createAopProxy().getProxyClass(classLoader);
	}


	/**
	 * Create a new proxy for the given interface and interceptor.
	 * <p>Convenience method for creating a proxy for a single interceptor,
	 * assuming that the interceptor handles all calls itself rather than
	 * delegating to a target, like in the case of remoting proxies.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param interceptor the interceptor that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.aopalliance.intercept.Interceptor)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, Interceptor interceptor) {
		return (T) new ProxyFactory(proxyInterface, interceptor).getProxy();
	}

	/**
	 * Create a proxy for the specified {@code TargetSource},
	 * implementing the specified interface.
	 * @param proxyInterface the interface that the proxy should implement
	 * @param targetSource the TargetSource that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.springframework.aop.TargetSource)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, TargetSource targetSource) {
		return (T) new ProxyFactory(proxyInterface, targetSource).getProxy();
	}

	/**
	 * [静态工具方法]：强行对类进行代理（CGLIB）。
	 * 哪怕你有接口，这个方法也会通过 setProxyTargetClass(true) 强迫使用 CGLIB 继承方式生成代理。
	 */
	public static Object getProxy(TargetSource targetSource) {
		if (targetSource.getTargetClass() == null) {
			throw new IllegalArgumentException("Cannot create class proxy for TargetSource with null target class");
		}
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.setProxyTargetClass(true);
		return proxyFactory.getProxy();
	}

}
