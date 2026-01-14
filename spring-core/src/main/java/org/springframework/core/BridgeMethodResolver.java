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

package org.springframework.core;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;

/**
 * Helper for resolving synthetic {@link Method#isBridge bridge Methods} to the
 * {@link Method} being bridged.
 *
 * <p>Given a synthetic {@link Method#isBridge bridge Method} returns the {@link Method}
 * being bridged. A bridge method may be created by the compiler when extending a
 * parameterized type whose methods have parameterized arguments. During runtime
 * invocation the bridge {@link Method} may be invoked and/or used via reflection.
 * When attempting to locate annotations on {@link Method Methods}, it is wise to check
 * for bridge {@link Method Methods} as appropriate and find the bridged {@link Method}.
 *
 * <p>See <a href="https://java.sun.com/docs/books/jls/third_edition/html/expressions.html#15.12.4.5">
 * The Java Language Specification</a> for more details on the use of bridge methods.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Phillip Webb
 * @since 2.0
 */
public final class BridgeMethodResolver {

	private static final Map<Object, Method> cache = new ConcurrentReferenceHashMap<>();

	private BridgeMethodResolver() {
	}


	/**
	 * Find the local original method for the supplied {@link Method bridge Method}.
	 * <p>It is safe to call this method passing in a non-bridge {@link Method} instance.
	 * In such a case, the supplied {@link Method} instance is returned directly to the caller.
	 * Callers are <strong>not</strong> required to check for bridging before calling this method.
	 * @param bridgeMethod the method to introspect against its declaring class
	 * @return the original method (either the bridged method or the passed-in method
	 * if no more specific one could be found)
	 * @see #getMostSpecificMethod(Method, Class)
	 */
	public static Method findBridgedMethod(Method bridgeMethod) {
		return resolveBridgeMethod(bridgeMethod, bridgeMethod.getDeclaringClass());
	}

	/**
	 * Determine the most specific method for the supplied {@link Method bridge Method}
	 * in the given class hierarchy, even if not available on the local declaring class.
	 * <p>This is effectively a combination of {@link ClassUtils#getMostSpecificMethod}
	 * and {@link #findBridgedMethod}, resolving the original method even if no bridge
	 * method has been generated at the same class hierarchy level (a known difference
	 * between the Eclipse compiler and regular javac).
	 * @param bridgeMethod the method to introspect against the given target class
	 * @param targetClass the target class to find the most specific method on
	 * @return the most specific method corresponding to the given bridge method
	 * (can be the original method if no more specific one could be found)
	 * @since 6.1.3
	 * @see #findBridgedMethod
	 * @see org.springframework.util.ClassUtils#getMostSpecificMethod
	 */
	public static Method getMostSpecificMethod(Method bridgeMethod, @Nullable Class<?> targetClass) {
		if (targetClass != null &&
				!ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()).isAssignableFrom(targetClass) &&
				!Proxy.isProxyClass(bridgeMethod.getDeclaringClass())) {
			// From a different class hierarchy, and not a JDK or CGLIB proxy either -> return as-is.
			return bridgeMethod;
		}

		Method specificMethod = ClassUtils.getMostSpecificMethod(bridgeMethod, targetClass);
		return resolveBridgeMethod(specificMethod,
				(targetClass != null ? targetClass : specificMethod.getDeclaringClass()));
	}

	/**
	 * 尝试解析原本的“桥接方法”对应的“实际方法”。
	 * <p>
	 * 如果传入的方法是一个编译器生成的 Bridge Method（通常参数被擦除为 Object），
	 * 该方法会尝试找到该类中同名、参数匹配的原始泛型方法。
	 *
	 * @param bridgeMethod 传入的可能是一个桥接方法
	 * @param targetClass 方法所在的声明类
	 * @return 解析后的原始方法，如果解析失败或不需要解析，则返回原方法
	 */
	private static Method resolveBridgeMethod(Method bridgeMethod, Class<?> targetClass) {
		// 1. 判断是否是“本地”方法（即：这个方法是不是就在 targetClass 这个类里声明的，而非父类继承）
		boolean localBridge = (targetClass == bridgeMethod.getDeclaringClass());
		Class<?> userClass = targetClass;

		// 2. 【快速通道】如果它根本不是桥接方法，且是本地声明的，直接返回，别浪费时间查缓存和反射
		if (!bridgeMethod.isBridge() && localBridge) {
			// 获取原本的类（处理 CGLIB 代理类的情况，拿到被代理的目标类）
			userClass = ClassUtils.getUserClass(targetClass);
			// 如果不是代理类，直接返回原方法
			if (userClass == targetClass) {
				return bridgeMethod;
			}
		}

		// 3. 【查缓存】为了性能，解析过的结果会存起来
		// 如果是本地方法，key就是方法本身；如果是继承的或代理的，key包含类信息
		Object cacheKey = (localBridge ? bridgeMethod : new MethodClassKey(bridgeMethod, targetClass));
		Method bridgedMethod = cache.get(cacheKey);

		// 4. 【核心逻辑】如果缓存没命中，开始干活
		if (bridgedMethod == null) {
			// 准备一个列表，用来存放候选的“真身”方法
			List<Method> candidateMethods = new ArrayList<>();

			// 定义过滤器：什么样的才算是“真身”？
			// 逻辑通常是：名字相同、参数个数相同、不是桥接方法本身
			MethodFilter filter = (candidateMethod -> isBridgedCandidateFor(candidateMethod, bridgeMethod));

			// 5. 【搜寻】遍历 userClass 的所有方法，利用过滤器筛选
			ReflectionUtils.doWithMethods(userClass, candidateMethods::add, filter);

			if (!candidateMethods.isEmpty()) {
				// 6. 【择优】
				// 如果只找到一个候选者，那肯定就是它了
				// 如果找到多个（极其罕见），调用 searchCandidates 进行更复杂的返回值/泛型匹配
				bridgedMethod = (candidateMethods.size() == 1 ? candidateMethods.get(0) :
						searchCandidates(candidateMethods, bridgeMethod));
			}

			// 7. 【兜底】如果找了一圈没找到（或者本来就没真身），那就还是用传入的这个方法
			if (bridgedMethod == null) {
				bridgedMethod = bridgeMethod;
			}

			// 8. 放入缓存，下次直接用
			cache.put(cacheKey, bridgedMethod);
		}
		return bridgedMethod;
	}

	/**
	 * Returns {@code true} if the supplied '{@code candidateMethod}' can be
	 * considered a valid candidate for the {@link Method} that is {@link Method#isBridge() bridged}
	 * by the supplied {@link Method bridge Method}. This method performs inexpensive
	 * checks and can be used to quickly filter for a set of possible matches.
	 */
	private static boolean isBridgedCandidateFor(Method candidateMethod, Method bridgeMethod) {
		return (!candidateMethod.isBridge() &&
				candidateMethod.getName().equals(bridgeMethod.getName()) &&
				candidateMethod.getParameterCount() == bridgeMethod.getParameterCount());
	}

	/**
	 * <b>在候选方法列表中搜寻真正的“被桥接方法”（真身）。</b>
	 *
	 * @param candidateMethods 候选方法列表（这些方法的名字和参数个数已经和 bridgeMethod 一样了）
	 * @param bridgeMethod 编译器生成的那个桥接方法（通常参数是 Object）
	 * @return 找到的真身方法，如果没找到或是无法确定，返回 null
	 */
	private static @Nullable Method searchCandidates(List<Method> candidateMethods, Method bridgeMethod) {
		// 0. 防御性检查
		if (candidateMethods.isEmpty()) {
			return null;
		}

		Method previousMethod = null;
		// 标记：是否所有候选者的泛型参数签名都完全一致？
		boolean sameSig = true;

		// 1. 遍历所有候选者，试图找到“真爱”
		for (Method candidateMethod : candidateMethods) {

			// 【核心判断】：利用泛型工具检查，candidateMethod 是否就是 bridgeMethod 的源头？
			// 比如：检查 bridgeMethod 的签名是否兼容 candidateMethod 的泛型签名。
			if (isBridgeMethodFor(bridgeMethod, candidateMethod, bridgeMethod.getDeclaringClass())) {
				return candidateMethod; // 找到了！直接返回，结束战斗。
			}

			// 如果不是“真爱”，我们顺便做个统计：
			// 检查当前这个候选者，和上一个候选者，参数签名是不是一样的？
			else if (previousMethod != null) {
				// 只要有一个不一样，sameSig 就变成 false
				sameSig = sameSig && Arrays.equals(
						candidateMethod.getGenericParameterTypes(), previousMethod.getGenericParameterTypes());
			}
			previousMethod = candidateMethod;
		}

		// 2. 兜底逻辑
		// 如果循环跑完了都没找到 isBridgeMethodFor 返回 true 的那个方法。
		// 但是！如果所有候选者的参数签名其实都是一模一样的（sameSig == true），
		// 那说明这几个候选者其实没本质区别（可能是父类子类重复定义的同签名方法），
		// 这种情况下，返回第一个候选者通常是安全的。
		// 否则，为了防止指鹿为马，返回 null。
		return (sameSig ? candidateMethods.get(0) : null);
	}

	/**
	 * Determines whether the bridge {@link Method} is the bridge for the
	 * supplied candidate {@link Method}.
	 */
	static boolean isBridgeMethodFor(Method bridgeMethod, Method candidateMethod, Class<?> declaringClass) {
		if (isResolvedTypeMatch(candidateMethod, bridgeMethod, declaringClass)) {
			return true;
		}
		Method method = findGenericDeclaration(bridgeMethod);
		return (method != null && isResolvedTypeMatch(method, candidateMethod, declaringClass));
	}

	/**
	 * Returns {@code true} if the {@link Type} signature of both the supplied
	 * {@link Method#getGenericParameterTypes() generic Method} and concrete {@link Method}
	 * are equal after resolving all types against the declaringType, otherwise
	 * returns {@code false}.
	 */
	private static boolean isResolvedTypeMatch(Method genericMethod, Method candidateMethod, Class<?> declaringClass) {
		Type[] genericParameters = genericMethod.getGenericParameterTypes();
		if (genericParameters.length != candidateMethod.getParameterCount()) {
			return false;
		}
		Class<?>[] candidateParameters = candidateMethod.getParameterTypes();
		for (int i = 0; i < candidateParameters.length; i++) {
			ResolvableType genericParameter = ResolvableType.forMethodParameter(genericMethod, i, declaringClass);
			Class<?> candidateParameter = candidateParameters[i];
			if (candidateParameter.isArray()) {
				// An array type: compare the component type.
				if (!candidateParameter.componentType().equals(genericParameter.getComponentType().toClass())) {
					return false;
				}
			}
			// A non-array type: compare the type itself.
			if (!ClassUtils.resolvePrimitiveIfNecessary(candidateParameter).equals(
					ClassUtils.resolvePrimitiveIfNecessary(genericParameter.toClass()))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Searches for the generic {@link Method} declaration whose erased signature
	 * matches that of the supplied bridge method.
	 * @throws IllegalStateException if the generic declaration cannot be found
	 */
	private static @Nullable Method findGenericDeclaration(Method bridgeMethod) {
		if (!bridgeMethod.isBridge()) {
			return bridgeMethod;
		}

		// Search parent types for method that has same signature as bridge.
		Class<?> superclass = bridgeMethod.getDeclaringClass().getSuperclass();
		while (superclass != null && Object.class != superclass) {
			Method method = searchForMatch(superclass, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			superclass = superclass.getSuperclass();
		}

		Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(bridgeMethod.getDeclaringClass());
		return searchInterfaces(interfaces, bridgeMethod);
	}

	private static @Nullable Method searchInterfaces(Class<?>[] interfaces, Method bridgeMethod) {
		for (Class<?> ifc : interfaces) {
			Method method = searchForMatch(ifc, bridgeMethod);
			if (method != null && !method.isBridge()) {
				return method;
			}
			else {
				method = searchInterfaces(ifc.getInterfaces(), bridgeMethod);
				if (method != null) {
					return method;
				}
			}
		}
		return null;
	}

	/**
	 * If the supplied {@link Class} has a declared {@link Method} whose signature matches
	 * that of the supplied {@link Method}, then this matching {@link Method} is returned,
	 * otherwise {@code null} is returned.
	 */
	private static @Nullable Method searchForMatch(Class<?> type, Method bridgeMethod) {
		try {
			return type.getDeclaredMethod(bridgeMethod.getName(), bridgeMethod.getParameterTypes());
		}
		catch (NoSuchMethodException ex) {
			return null;
		}
	}

	/**
	 * Compare the signatures of the bridge method and the method which it bridges. If
	 * the parameter and return types are the same, it is a 'visibility' bridge method
	 * introduced in Java 6 to fix <a href="https://bugs.openjdk.org/browse/JDK-6342411">
	 * JDK-6342411</a>.
	 * @return whether signatures match as described
	 */
	public static boolean isVisibilityBridgeMethodPair(Method bridgeMethod, Method bridgedMethod) {
		if (bridgeMethod == bridgedMethod) {
			// Same method: for common purposes, return true to proceed as if it was a visibility bridge.
			return true;
		}
		if (ClassUtils.getUserClass(bridgeMethod.getDeclaringClass()) != bridgeMethod.getDeclaringClass()) {
			// Method on generated subclass: return false to consistently ignore it for visibility purposes.
			return false;
		}
		return (bridgeMethod.getReturnType().equals(bridgedMethod.getReturnType()) &&
				bridgeMethod.getParameterCount() == bridgedMethod.getParameterCount() &&
				Arrays.equals(bridgeMethod.getParameterTypes(), bridgedMethod.getParameterTypes()));
	}

}
