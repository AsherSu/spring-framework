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

/**
 * Spring 切点（Pointcut）的核心抽象接口。
 *
 * <p>一个 Pointcut 由两部分组成：
 * 1. {@link ClassFilter}：用于过滤类。
 * 2. {@link MethodMatcher}：用于过滤方法。
 * * <p>这两个基础组件以及 Pointcut 本身都可以组合使用
 * （例如，通过 {@link org.springframework.aop.support.ComposablePointcut} 进行组合）。
 *
 * @author Rod Johnson
 * @see ClassFilter
 * @see MethodMatcher
 * @see org.springframework.aop.support.Pointcuts
 * @see org.springframework.aop.support.ClassFilters
 * @see org.springframework.aop.support.MethodMatchers
 */
public interface Pointcut {

	/**
	 * 返回此切点的类过滤器（ClassFilter）。
	 * <p>作用：在方法匹配之前，先对目标类进行粗粒度的筛选。
	 * 如果 ClassFilter 的 {@code matches(Class)} 方法返回 false，
	 * 则该切点完全不适用于该类，Spring 将跳过后续的方法匹配检查（性能优化关键）。
	 *
	 * @return ClassFilter 对象 (绝不为 {@code null})
	 */
	ClassFilter getClassFilter();

	/**
	 * 返回此切点的方法匹配器（MethodMatcher）。
	 * <p>作用：在 ClassFilter 匹配通过后，对类中的具体方法进行细粒度的筛选。
	 * 它决定了具体的某个方法执行时，是否应用增强（Advice）。
	 *
	 * @return MethodMatcher 对象 (绝不为 {@code null})
	 */
	MethodMatcher getMethodMatcher();


	/**
	 * 一个总是匹配的规范 Pointcut 实例（单例）。
	 * <p>它的 ClassFilter 匹配所有类，MethodMatcher 匹配所有方法。
	 * 通常用于默认场景或者总是需要增强的场景。
	 */
	Pointcut TRUE = TruePointcut.INSTANCE;

}
