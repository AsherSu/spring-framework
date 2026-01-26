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

package org.springframework.aop.support;

import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.Advisor;
import org.springframework.aop.AopInvocationException;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetClassAware;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodIntrospector;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility methods for AOP support code.
 *
 * <p>Mainly for internal use within Spring's AOP support.
 *
 * <p>See {@link org.springframework.aop.framework.AopProxyUtils} for a
 * collection of framework-specific AOP utility methods which depend
 * on internals of Spring's AOP framework implementation.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Sebastien Deleuze
 * @see org.springframework.aop.framework.AopProxyUtils
 */
public abstract class AopUtils {

	private static final boolean coroutinesReactorPresent = ClassUtils.isPresent(
			"kotlinx.coroutines.reactor.MonoKt", AopUtils.class.getClassLoader());


	/**
	 * Check whether the given object is a JDK dynamic proxy or a CGLIB proxy.
	 * <p>This method additionally checks if the given object is an instance
	 * of {@link SpringProxy}.
	 * @param object the object to check
	 * @see #isJdkDynamicProxy
	 * @see #isCglibProxy
	 */
	@Contract("null -> false")
	public static boolean isAopProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && (Proxy.isProxyClass(object.getClass()) ||
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR)));
	}

	/**
	 * Check whether the given object is a JDK dynamic proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link Proxy#isProxyClass(Class)} by additionally checking if the
	 * given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see java.lang.reflect.Proxy#isProxyClass
	 */
	@Contract("null -> false")
	public static boolean isJdkDynamicProxy(@Nullable Object object) {
		return (object instanceof SpringProxy && Proxy.isProxyClass(object.getClass()));
	}

	/**
	 * Check whether the given object is a CGLIB proxy.
	 * <p>This method goes beyond the implementation of
	 * {@link ClassUtils#isCglibProxy(Object)} by additionally checking if
	 * the given object is an instance of {@link SpringProxy}.
	 * @param object the object to check
	 * @see ClassUtils#isCglibProxy(Object)
	 */
	@Contract("null -> false")
	public static boolean isCglibProxy(@Nullable Object object) {
		return (object instanceof SpringProxy &&
				object.getClass().getName().contains(ClassUtils.CGLIB_CLASS_SEPARATOR));
	}

	/**
	 * Determine the target class of the given bean instance which might be an AOP proxy.
	 * <p>Returns the target class for an AOP proxy or the plain class otherwise.
	 * @param candidate the instance to check (might be an AOP proxy)
	 * @return the target class (or the plain class of the given object as fallback;
	 * never {@code null})
	 * @see org.springframework.aop.TargetClassAware#getTargetClass()
	 * @see org.springframework.aop.framework.AopProxyUtils#ultimateTargetClass(Object)
	 */
	public static Class<?> getTargetClass(Object candidate) {
		Assert.notNull(candidate, "Candidate object must not be null");
		Class<?> result = null;
		if (candidate instanceof TargetClassAware targetClassAware) {
			result = targetClassAware.getTargetClass();
		}
		if (result == null) {
			result = (isCglibProxy(candidate) ? candidate.getClass().getSuperclass() : candidate.getClass());
		}
		return result;
	}

	/**
	 * Select an invocable method on the target type: either the given method itself
	 * if actually exposed on the target type, or otherwise a corresponding method
	 * on one of the target type's interfaces or on the target type itself.
	 * @param method the method to check
	 * @param targetType the target type to search methods on (typically an AOP proxy)
	 * @return a corresponding invocable method on the target type
	 * @throws IllegalStateException if the given method is not invocable on the given
	 * target type (typically due to a proxy mismatch)
	 * @since 4.3
	 * @see MethodIntrospector#selectInvocableMethod(Method, Class)
	 */
	public static Method selectInvocableMethod(Method method, @Nullable Class<?> targetType) {
		if (targetType == null) {
			return method;
		}
		Method methodToUse = MethodIntrospector.selectInvocableMethod(method, targetType);
		if (Modifier.isPrivate(methodToUse.getModifiers()) && !Modifier.isStatic(methodToUse.getModifiers()) &&
				SpringProxy.class.isAssignableFrom(targetType)) {
			throw new IllegalStateException(String.format(
					"Need to invoke method '%s' found on proxy for target class '%s' but cannot " +
					"be delegated to target bean. Switch its visibility to package or protected.",
					method.getName(), method.getDeclaringClass().getSimpleName()));
		}
		return methodToUse;
	}

	/**
	 * Determine whether the given method is an "equals" method.
	 * @see java.lang.Object#equals
	 */
	public static boolean isEqualsMethod(@Nullable Method method) {
		return ReflectionUtils.isEqualsMethod(method);
	}

	/**
	 * Determine whether the given method is a "hashCode" method.
	 * @see java.lang.Object#hashCode
	 */
	public static boolean isHashCodeMethod(@Nullable Method method) {
		return ReflectionUtils.isHashCodeMethod(method);
	}

	/**
	 * Determine whether the given method is a "toString" method.
	 * @see java.lang.Object#toString()
	 */
	public static boolean isToStringMethod(@Nullable Method method) {
		return ReflectionUtils.isToStringMethod(method);
	}

	/**
	 * Determine whether the given method is a "finalize" method.
	 * @see java.lang.Object#finalize()
	 */
	public static boolean isFinalizeMethod(@Nullable Method method) {
		return (method != null && method.getName().equals("finalize") &&
				method.getParameterCount() == 0);
	}

	/**
	 * Given a method, which may come from an interface, and a target class used
	 * in the current AOP invocation, find the corresponding target method if there
	 * is one. For example, the method may be {@code IFoo.bar()} and the target class
	 * may be {@code DefaultFoo}. In this case, the method may be
	 * {@code DefaultFoo.bar()}. This enables attributes on that method to be found.
	 * <p><b>NOTE:</b> In contrast to {@link org.springframework.util.ClassUtils#getMostSpecificMethod},
	 * this method resolves bridge methods in order to retrieve attributes from
	 * the <i>original</i> method definition.
	 * @param method the method to be invoked, which may come from an interface
	 * @param targetClass the target class for the current invocation
	 * (can be {@code null} or may not even implement the method)
	 * @return the specific target method, or the original method if the
	 * {@code targetClass} does not implement it
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 * @see org.springframework.core.BridgeMethodResolver#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method method, @Nullable Class<?> targetClass) {
		Class<?> specificTargetClass = (targetClass != null ? ClassUtils.getUserClass(targetClass) : null);
		return BridgeMethodResolver.getMostSpecificMethod(method, specificTargetClass);
	}

	/**
	 * Can the given pointcut apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize
	 * out a pointcut for a class.
	 * @param pc the static or dynamic pointcut to check
	 * @param targetClass the class to test
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass) {
		return canApply(pc, targetClass, false);
	}

	/**
	 * 判断给定的切点（Pointcut）是否能够应用在给定的目标类上？
	 * <p>这是一个重要的测试，用于优化步骤：如果一个切点对某个类完全不适用，
	 * 就可以将该切点从该类的代理流程中排除。
	 *
	 * @param pc            要检查的静态或动态切点
	 * @param targetClass   要测试的目标类
	 * @param hasIntroductions 该 Bean 的顾问链（Advisor chain）中是否包含引介（Introductions）
	 * （引介是指给类动态添加新的接口或方法）
	 * @return 切点是否可以应用在目标类的【任意】一个方法上
	 */
	public static boolean canApply(Pointcut pc, Class<?> targetClass, boolean hasIntroductions) {
		Assert.notNull(pc, "Pointcut must not be null");

		// 1. 初步筛选：使用 ClassFilter 进行类级别的检查
		// 如果切点的 ClassFilter 直接排除了这个目标类，那么其下的任何方法肯定都不匹配，直接返回 false。
		if (!pc.getClassFilter().matches(targetClass)) {
			return false;
		}

		MethodMatcher methodMatcher = pc.getMethodMatcher();
		// 2. 快速优化：检查 MethodMatcher 是否为 "匹配所有" (MethodMatcher.TRUE)
		// 如果该切点匹配该类（上面已通过），且匹配该类的所有方法，则无需遍历具体方法，直接返回 true。
		if (methodMatcher == MethodMatcher.TRUE) {
			// No need to iterate the methods if we're matching any method anyway...
			return true;
		}

		// 3. 准备 IntroductionAwareMethodMatcher
		// 某些特殊的匹配器（如 AspectJ 的表达式匹配器）需要感知 "引介" (Introductions)，
		// 因为引介可能会改变类的方法结构。
		IntroductionAwareMethodMatcher introductionAwareMethodMatcher = null;
		if (methodMatcher instanceof IntroductionAwareMethodMatcher iamm) {
			introductionAwareMethodMatcher = iamm;
		}

		// 4. 收集需要扫描的所有类和接口
		// Spring AOP 不仅要检查类本身的方法，还要检查它实现的所有接口的方法（因为 JDK 动态代理是基于接口的）。
		Set<Class<?>> classes = new LinkedHashSet<>();
		// 如果不是 Proxy 类（即不是已经代理过的类），获取其原始的用户类（处理 CGLIB 包装的情况）
		if (!Proxy.isProxyClass(targetClass)) {
			classes.add(ClassUtils.getUserClass(targetClass));
		}
		// 将该类实现的所有接口也加入扫描列表
		classes.addAll(ClassUtils.getAllInterfacesForClassAsSet(targetClass));

		// 5. 遍历类和接口中的每一个方法
		for (Class<?> clazz : classes) {
			// 获取当前类/接口声明的所有方法
			Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);
			for (Method method : methods) {
				// 6. 执行方法匹配
				// 只要找到【任何一个】匹配的方法，就说明这个切点适用于这个类，返回 true。
				if (introductionAwareMethodMatcher != null ?
						// 如果是特殊的匹配器，传入 hasIntroductions 参数进行判断
						introductionAwareMethodMatcher.matches(method, targetClass, hasIntroductions) :
						// 普通匹配器，只根据方法和目标类判断
						methodMatcher.matches(method, targetClass)) {
					return true;
				}
			}
		}

		// 7. 如果遍历了所有类和接口的所有方法，都没有找到匹配的，说明该切点完全不适用于该类。
		return false;
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * This is an important test as it can be used to optimize
	 * out an advisor for a class.
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass) {
		return canApply(advisor, targetClass, false);
	}

	/**
	 * Can the given advisor apply at all on the given class?
	 * <p>This is an important test as it can be used to optimize out an advisor for a class.
	 * This version also takes into account introductions (for IntroductionAwareMethodMatchers).
	 * @param advisor the advisor to check
	 * @param targetClass class we're testing
	 * @param hasIntroductions whether the advisor chain for this bean includes
	 * any introductions
	 * @return whether the pointcut can apply on any method
	 */
	public static boolean canApply(Advisor advisor, Class<?> targetClass, boolean hasIntroductions) {
		if (advisor instanceof IntroductionAdvisor ia) {
			return ia.getClassFilter().matches(targetClass);
		}
		else if (advisor instanceof PointcutAdvisor pca) {
			return canApply(pca.getPointcut(), targetClass, hasIntroductions);
		}
		else {
			// It doesn't have a pointcut so we assume it applies.
			return true;
		}
	}

	/**
	 * 确定 {@code candidateAdvisors} 列表中适用于给定类的子列表。
	 * @param candidateAdvisors 要评估的顾问列表
	 * @param clazz 目标类
	 * @return 可应用于给定类的顾问子列表
	 * （可能是原始列表）
	 */
	public static List<Advisor> findAdvisorsThatCanApply(List<Advisor> candidateAdvisors, Class<?> clazz) {
		// 如果候选Advisor列表为空，则直接返回空列表
		if (candidateAdvisors.isEmpty()) {
			return candidateAdvisors;
		}
		// 创建一个用于存储适用于给定类的Advisor的列表
		List<Advisor> eligibleAdvisors = new ArrayList<>();
		// 遍历候选Advisor列表
		for (Advisor candidate : candidateAdvisors) {
			// 如果候选Advisor是IntroductionAdvisor，并且可以应用于给定类，则将其添加到结果列表中
			if (candidate instanceof IntroductionAdvisor && canApply(candidate, clazz)) {
				eligibleAdvisors.add(candidate);
			}
		}
		// 检查是否存在引介Advisor
		boolean hasIntroductions = !eligibleAdvisors.isEmpty();
		// 继续遍历候选顾问列表
		for (Advisor candidate : candidateAdvisors) {
			// 如果候选顾问是引介顾问，则跳过
			if (candidate instanceof IntroductionAdvisor) {
				// 已经处理过
				continue;
			}
			// 如果候选顾问可以应用于给定类，则将其添加到结果列表中
			if (canApply(candidate, clazz, hasIntroductions)) {
				eligibleAdvisors.add(candidate);
			}
		}
		return eligibleAdvisors;
	}

	/**
	 * 使用反射调用给定的目标方法，作为AOP方法调用的一部分。
	 * @param target 目标对象
	 * @param method 要调用的方法
	 * @param args 方法的参数
	 * @return 调用结果，如果有的话
	 * @throws Throwable 如果目标方法抛出异常
	 * @throws org.springframework.aop.AopInvocationException 如果发生反射错误
	 */
	public static @Nullable Object invokeJoinpointUsingReflection(@Nullable Object target, Method method, @Nullable Object[] args)
			throws Throwable {

		// 使用反射调用方法
		try {
			Method originalMethod = BridgeMethodResolver.findBridgedMethod(method);
			ReflectionUtils.makeAccessible(originalMethod);
			return (coroutinesReactorPresent && KotlinDetector.isSuspendingFunction(originalMethod) ?
					KotlinDelegate.invokeSuspendingFunction(originalMethod, target, args) : originalMethod.invoke(target, args));
		}
		catch (InvocationTargetException ex) {
			// 调用的方法抛出了已检查的异常。
			// 我们必须重新抛出它。客户端不会看到拦截器。
			throw ex.getTargetException();
		}
		catch (IllegalArgumentException ex) {
			// 如果发生参数错误，则抛出AOP调用异常
			throw new AopInvocationException("AOP configuration seems to be invalid: tried calling method [" +
					method + "] on target [" + target + "]", ex);
		}
		catch (IllegalAccessException | InaccessibleObjectException ex) {
			// 如果无法访问方法，则抛出AOP调用异常
			throw new AopInvocationException("Could not access method [" + method + "]", ex);
		}
	}


	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static class KotlinDelegate {

		public static Object invokeSuspendingFunction(Method method, @Nullable Object target, @Nullable Object... args) {
			Continuation<?> continuation = (Continuation<?>) args[args.length -1];
			Assert.state(continuation != null, "No Continuation available");
			CoroutineContext context = continuation.getContext().minusKey(Job.Key);
			return CoroutinesUtils.invokeSuspendingFunction(context, method, target, args);
		}
	}

}
