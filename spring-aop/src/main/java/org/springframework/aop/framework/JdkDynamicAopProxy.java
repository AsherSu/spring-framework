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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @author Sergey Tsypanov
 * @author Sebastien Deleuze
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

	private static final boolean coroutinesReactorPresent = ClassUtils.isPresent(
			"kotlinx.coroutines.reactor.MonoKt", JdkDynamicAopProxy.class.getClassLoader());

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/** AdvisedSupport 持有一个 List<Advisor>属性 */
	private final AdvisedSupport advised;

	/** Cached in {@link AdvisedSupport#proxyMetadataCache}. */
	private transient ProxiedInterfacesCache cache;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		this.advised = config;

		// Initialize ProxiedInterfacesCache if not cached already
		ProxiedInterfacesCache cache;
		if (config.proxyMetadataCache instanceof ProxiedInterfacesCache proxiedInterfacesCache) {
			cache = proxiedInterfacesCache;
		}
		else {
			cache = new ProxiedInterfacesCache(config);
			config.proxyMetadataCache = cache;
		}
		this.cache = cache;
	}


	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		return Proxy.newProxyInstance(determineClassLoader(classLoader), this.cache.proxiedInterfaces, this);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
		return Proxy.getProxyClass(determineClassLoader(classLoader), this.cache.proxiedInterfaces);
	}

	/**
	 * Determine whether the JDK bootstrap or platform loader has been suggested ->
	 * use higher-level loader which can see Spring infrastructure classes instead.
	 */
	private ClassLoader determineClassLoader(@Nullable ClassLoader classLoader) {
		if (classLoader == null) {
			// JDK bootstrap loader -> use spring-aop ClassLoader instead.
			return getClass().getClassLoader();
		}
		if (classLoader.getParent() == null) {
			// Potentially the JDK platform loader on JDK 9+
			ClassLoader aopClassLoader = getClass().getClassLoader();
			ClassLoader aopParent = aopClassLoader.getParent();
			while (aopParent != null) {
				if (classLoader == aopParent) {
					// Suggested ClassLoader is ancestor of spring-aop ClassLoader
					// -> use spring-aop ClassLoader itself instead.
					return aopClassLoader;
				}
				aopParent = aopParent.getParent();
			}
		}
		// Regular case: use suggested ClassLoader as-is.
		return classLoader;
	}

	/**
	 * <b>JDK 动态代理的核心入口</b>
	 * <p>
	 * 当外界调用代理对象的任意接口方法时，都会进入这个方法。
	 * 它的核心任务是：<b>组装拦截器链 -> 执行切面逻辑 -> 调用目标方法</b>。
	 */
	@Override
	public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// 保存旧的代理对象，用于后续恢复（处理 AopContext 嵌套调用的情况）
		Object oldProxy = null;
		// 标记是否我们将当前代理暴露到了 ThreadLocal 中
		boolean setProxyContext = false;

		// 获取目标源（通常就是单例的 Bean，但也可能是原型 Bean 或对象池）
		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			/**
			 * ============================================================
			 * 1. 处理 Object 类的基本方法 (equals, hashCode)
			 * ============================================================
			 * 代理对象本身也需要进行相等性判断。如果目标对象没有重写这些方法，
			 * 代理类需要自己处理，否则比较内存地址会导致逻辑错误。
			 */
			if (!this.cache.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// 目标对象未实现 equals(Object) 方法
				return equals(args[0]);
			}
			else if (!this.cache.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// 目标对象未实现 hashCode() 方法
				return hashCode();
			}
			/**
			 * ============================================================
			 * 2. 处理 Spring 内部配置接口的方法
			 * ============================================================
			 */
			// 当前调用的方法是 ProxyConfig 接口的方法时
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// 这是一个内部标记接口，用于查询“bean到底代理了谁？”
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}

			else if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				// 如果调用的是 Advised 接口的方法（比如 addAdvice, removeAdvice），
				// 这些是管理代理配置的方法，直接反射调用配置对象，不需要走切面拦截。
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}

			Object retVal;

			/**
			 * ============================================================
			 * 3. 处理 expose-proxy (AopContext)
			 * ============================================================
			 * 场景：Bean 内部调用自己的方法（this.methodB()），默认是不走 AOP 的。
			 * 如果配置了 exposeProxy=true，这里会把当前代理对象塞到 ThreadLocal 里，
			 * 让业务代码能通过 AopContext.currentProxy() 拿到代理对象，从而实现自调用增强。
			 *
			 * eg. 业务代码
			 * <pre class="code">
        	 * 		// 从 ThreadLocal 里拿到第3步塞进去的代理对象
             * 		OrderService currentProxy = (OrderService) AopContext.currentProxy();
			 * 		// 通过代理对象调用，事务就生效了！
             * 		currentProxy.saveLog();
             * </pre>
			 */
			if (this.advised.isExposeProxy()) {
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// 获取当前的目标对象（被代理的真实对象）
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			/*
			 * ============================================================
			 * 4. 获取拦截器链 (最核心步骤)
			 * ============================================================
			 * 拿着方法和目标类，去问配置中心：“有哪些切面（事务、日志等）匹配这个方法？”
			 * 结果是一个列表 chain，里面装满了 MethodInterceptor。
			 */
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			/*
			 * ============================================================
			 * 5. 执行调用
			 * ============================================================
			 */
			if (chain.isEmpty()) {
				// 【优化路径】：如果没有定义任何拦截器/切面，那就别折腾了。
				// 既不创建 MethodInvocation，也不走递归链，直接反射调用目标方法。
				// 这样能节省大量性能开销。
				@Nullable Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			else {
				// 【标准 AOP 路径】：如果有拦截器。
				// 创建一个 ReflectiveMethodInvocation（核心驱动器），它封装了：
				// 代理对象、目标对象、方法、参数、目标类、以及最重要的——拦截器链。
				MethodInvocation invocation = new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);

				// 启动递归调用链：proceed() 会依次执行拦截器，最后才执行目标方法。
				retVal = invocation.proceed();
			}

			/*
			 * ============================================================
			 * 6. 处理返回值
			 * ============================================================
			 */
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// 【修正 this 引用】：
				// 如果目标方法返回了 `this`（也就是目标对象自己），但外部调用者期待的是代理对象。
				// 这时需要把返回值偷偷替换成 proxy，防止“原形毕露”（泄露了没有增强的原始对象）。
				retVal = proxy;
			}
			else if (retVal == null && returnType != void.class && returnType.isPrimitive()) {
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}

			// 【Kotlin 协程支持】：如果是 Kotlin 的挂起函数，需要特殊处理返回流。
			if (coroutinesReactorPresent && KotlinDetector.isSuspendingFunction(method)) {
				return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()) ?
						CoroutinesUtils.asFlow(retVal) : CoroutinesUtils.awaitSingleOrNull(retVal, args[args.length - 1]);
			}
			return retVal;
		}
		finally {
			/*
			 * ============================================================
			 * 7. 资源清理
			 * ============================================================
			 */
			if (target != null && !targetSource.isStatic()) {
				// 如果 TargetSource 是池化的（prototype），这里需要把对象归还给池子。
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// 恢复现场：把 ThreadLocal 里的代理对象还原成旧的。
				// 对应上面的 AopContext.setCurrentProxy 操作
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy jdkDynamicAopProxy) {
			otherProxy = jdkDynamicAopProxy;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy jdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = jdkDynamicAopProxy;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.cache = new ProxiedInterfacesCache(this.advised);
	}


	/**
	 * Holder for the complete proxied interfaces and derived metadata,
	 * to be cached in {@link AdvisedSupport#proxyMetadataCache}.
	 * @since 6.1.3
	 */
	private static final class ProxiedInterfacesCache {

		// 缓存代理需要实现的完整接口数组，避免重复解析。
		final Class<?>[] proxiedInterfaces;

		// 指示这些接口中是否已有自定义 equals 方法，用于决定是否由代理接管相等性比较。
		final boolean equalsDefined;

		// 指示这些接口中是否已有自定义 hashCode 方法，用于决定是否由代理计算哈希。
		final boolean hashCodeDefined;

		ProxiedInterfacesCache(AdvisedSupport config) {
			this.proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(config, true);

			// Find any {@link #equals} or {@link #hashCode} method that may be defined
			// on the supplied set of interfaces.
			boolean equalsDefined = false;
			boolean hashCodeDefined = false;
			for (Class<?> proxiedInterface : this.proxiedInterfaces) {
				Method[] methods = proxiedInterface.getDeclaredMethods();
				for (Method method : methods) {
					if (AopUtils.isEqualsMethod(method)) {
						equalsDefined = true;
						if (hashCodeDefined) {
							break;
						}
					}
					if (AopUtils.isHashCodeMethod(method)) {
						hashCodeDefined = true;
						if (equalsDefined) {
							break;
						}
					}
				}
				if (equalsDefined && hashCodeDefined) {
					break;
				}
			}
			this.equalsDefined = equalsDefined;
			this.hashCodeDefined = hashCodeDefined;
		}
	}

}
