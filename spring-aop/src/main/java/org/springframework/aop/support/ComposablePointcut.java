/*
 * Copyright 2002-present the original author or authors.
 * ...
 */

package org.springframework.aop.support;

import java.io.Serializable;

import org.jspecify.annotations.Nullable;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.util.Assert;

/**
 * 用于构建组合切点的便捷类。
 *
 * <p>所有的组合方法都返回 {@code ComposablePointcut} 本身，因此可以使用如下的链式编程风格：
 *
 * <pre class="code">Pointcut pc = new ComposablePointcut()
 * .union(classFilter)         // 或
 * .intersection(methodMatcher)// 与
 * .intersection(pointcut);    // 与</pre>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 11.11.2003
 * @see Pointcuts
 */
public class ComposablePointcut implements Pointcut, Serializable {

	/** 为了与 Spring 1.2 的互操作性而保留的 serialVersionUID */
	private static final long serialVersionUID = -2743223737633663832L;

	// 当前组合切点持有的类过滤器
	@SuppressWarnings("serial")
	private ClassFilter classFilter;

	// 当前组合切点持有的方法匹配器
	@SuppressWarnings("serial")
	private MethodMatcher methodMatcher;


	/**
	 * 创建一个默认的 ComposablePointcut。
	 * 初始化状态为匹配所有类 (ClassFilter.TRUE) 和所有方法 (MethodMatcher.TRUE)。
	 */
	public ComposablePointcut() {
		this.classFilter = ClassFilter.TRUE;
		this.methodMatcher = MethodMatcher.TRUE;
	}

	/**
	 * 基于给定的 Pointcut 创建一个 ComposablePointcut。
	 * @param pointcut 原始切点
	 */
	public ComposablePointcut(Pointcut pointcut) {
		Assert.notNull(pointcut, "Pointcut must not be null");
		this.classFilter = pointcut.getClassFilter();
		this.methodMatcher = pointcut.getMethodMatcher();
	}

	/**
	 * 基于给定的 ClassFilter 创建一个 ComposablePointcut。
	 * MethodMatcher 默认为匹配所有 (TRUE)。
	 * @param classFilter 要使用的类过滤器
	 */
	public ComposablePointcut(ClassFilter classFilter) {
		Assert.notNull(classFilter, "ClassFilter must not be null");
		this.classFilter = classFilter;
		this.methodMatcher = MethodMatcher.TRUE;
	}

	/**
	 * 基于给定的 MethodMatcher 创建一个 ComposablePointcut。
	 * ClassFilter 默认为匹配所有 (TRUE)。
	 * @param methodMatcher 要使用的方法匹配器
	 */
	public ComposablePointcut(MethodMatcher methodMatcher) {
		Assert.notNull(methodMatcher, "MethodMatcher must not be null");
		this.classFilter = ClassFilter.TRUE;
		this.methodMatcher = methodMatcher;
	}

	/**
	 * 基于给定的 ClassFilter 和 MethodMatcher 创建一个 ComposablePointcut。
	 * @param classFilter 要使用的类过滤器
	 * @param methodMatcher 要使用的方法匹配器
	 */
	public ComposablePointcut(ClassFilter classFilter, MethodMatcher methodMatcher) {
		Assert.notNull(classFilter, "ClassFilter must not be null");
		Assert.notNull(methodMatcher, "MethodMatcher must not be null");
		this.classFilter = classFilter;
		this.methodMatcher = methodMatcher;
	}


	/**
	 * 对当前的 ClassFilter 执行【并集 (Union / OR)】操作。
	 * 结果是：当前过滤器匹配 OR 新过滤器匹配。
	 * @param other 要合并的类过滤器
	 * @return 返回当前的组合切点 (用于链式调用)
	 */
	public ComposablePointcut union(ClassFilter other) {
		// 调用工具类 ClassFilters.union 生成一个新的组合 ClassFilter
		this.classFilter = ClassFilters.union(this.classFilter, other);
		return this;
	}

	/**
	 * 对当前的 ClassFilter 执行【交集 (Intersection / AND)】操作。
	 * 结果是：当前过滤器匹配 AND 新过滤器匹配。
	 * @param other 要取交集的类过滤器
	 * @return 返回当前的组合切点 (用于链式调用)
	 */
	public ComposablePointcut intersection(ClassFilter other) {
		this.classFilter = ClassFilters.intersection(this.classFilter, other);
		return this;
	}

	/**
	 * 对当前的 MethodMatcher 执行【并集 (Union / OR)】操作。
	 * 结果是：当前匹配器匹配 OR 新匹配器匹配。
	 * @param other 要合并的方法匹配器
	 * @return 返回当前的组合切点
	 */
	public ComposablePointcut union(MethodMatcher other) {
		this.methodMatcher = MethodMatchers.union(this.methodMatcher, other);
		return this;
	}

	/**
	 * 对当前的 MethodMatcher 执行【交集 (Intersection / AND)】操作。
	 * 结果是：当前匹配器匹配 AND 新匹配器匹配。
	 * @param other 要取交集的方法匹配器
	 * @return 返回当前的组合切点
	 */
	public ComposablePointcut intersection(MethodMatcher other) {
		this.methodMatcher = MethodMatchers.intersection(this.methodMatcher, other);
		return this;
	}

	/**
	 * 对给定的 Pointcut 执行【并集 (Union / OR)】操作。
	 * <p>注意：对于 Pointcut 的并集操作，方法只有在它原本所属的 Pointcut 的 ClassFilter
	 * 也匹配的情况下才算匹配。
	 * 来自不同 Pointcut 的 MethodMatcher 和 ClassFilter 不会交叉匹配。
	 *
	 * @param other 要合并的 Pointcut
	 * @return 返回当前的组合切点
	 */
	public ComposablePointcut union(Pointcut other) {
		// 这里调用了一个特殊的 MethodMatchers.union 重载方法
		// 它需要同时传入两个 Pointcut 的 MethodMatcher 和 ClassFilter
		// 这是为了确保逻辑的正确性：(A类 && A方法) || (B类 && B方法)
		// 而不能简单的变成 (A类 || B类) && (A方法 || B方法)，那是错误的逻辑。
		this.methodMatcher = MethodMatchers.union(
				this.methodMatcher, this.classFilter, other.getMethodMatcher(), other.getClassFilter());

		// 更新 ClassFilter 为两者的并集
		this.classFilter = ClassFilters.union(this.classFilter, other.getClassFilter());
		return this;
	}

	/**
	 * 对给定的 Pointcut 执行【交集 (Intersection / AND)】操作。
	 * @param other 要取交集的 Pointcut
	 * @return 返回当前的组合切点
	 */
	public ComposablePointcut intersection(Pointcut other) {
		// 逻辑：(当前类 && 新类) 且 (当前方法 && 新方法)
		this.classFilter = ClassFilters.intersection(this.classFilter, other.getClassFilter());
		this.methodMatcher = MethodMatchers.intersection(this.methodMatcher, other.getMethodMatcher());
		return this;
	}


	// --- 实现 Pointcut 接口的方法 ---

	@Override
	public ClassFilter getClassFilter() {
		return this.classFilter;
	}

	@Override
	public MethodMatcher getMethodMatcher() {
		return this.methodMatcher;
	}

	// --- Object 方法重写 ---

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ComposablePointcut otherPointcut &&
				this.classFilter.equals(otherPointcut.classFilter) &&
				this.methodMatcher.equals(otherPointcut.methodMatcher)));
	}

	@Override
	public int hashCode() {
		return this.classFilter.hashCode() * 37 + this.methodMatcher.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + this.classFilter + ", " + this.methodMatcher;
	}

}