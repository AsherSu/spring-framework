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

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;

/**
 * <b>Spring AOP 的“通知适配器”接口。</b>
 *
 * <p><b>大白话解释：</b><br>
 * Spring AOP 的底层运行机制其实只认一种东西，那就是 {@link org.aopalliance.intercept.MethodInterceptor}（方法拦截器）。
 * <br>
 * 但是，Spring 为了方便大家开发，提供了很多特定类型的通知，比如“前置通知”（BeforeAdvice）、“异常通知”（ThrowsAdvice）等。
 * 这些特定的通知类型，Spring 底层是不能直接调用的。
 * <br>
 * <b>这个接口的作用就是个“转换插头”：</b>它负责把那些 Spring 底层不直接支持的特殊 Advice（通知），
 * 包装成一个标准的 MethodInterceptor（拦截器），这样 Spring 就可以统一处理它们了。
 *
 * <p><b>谁需要关注它：</b><br>
 * 99% 的 Spring 使用者都不需要关心这个接口。只有当你想要扩展 Spring 框架，
 * 发明一种全新的 Advice 类型（不仅仅是前置、后置那么简单）时，才需要实现这个接口来告诉 Spring 如何适配它。
 *
 * @author Rod Johnson
 */
public interface AdvisorAdapter {

	/**
	 * <b>资格审查：你能不能处理这种通知？</b>
	 *
	 * <p>询问当前的适配器，是否认识并支持处理传入的这个 {@code advice} 对象。
	 * <br>比如：{@code MethodBeforeAdviceAdapter}（前置通知适配器）只会对
	 * {@code MethodBeforeAdvice}（前置通知）返回 true，给它一个“异常通知”它就会返回 false。
	 *
	 * @param advice 需要被适配的通知对象（比如一个 BeforeAdvice）
	 * @return 如果此适配器能把这个 advice 转换成拦截器，则返回 true；否则返回 false。
	 */
	boolean supportsAdvice(Advice advice);

	/**
	 * <b>执行转换：把 Advisor 里的通知变成拦截器。</b>
	 *
	 * <p>将传入的 {@code advisor}（里面包含了具体的 Advice）转换成一个符合 AOP Alliance 标准的
	 * {@link org.aopalliance.intercept.MethodInterceptor}。
	 *
	 * <p><b>注意：</b><br>
	 * 这个方法只负责把“通知逻辑”转换成“拦截逻辑”。至于这个通知应该在哪些方法上执行（即 Pointcut 切点逻辑），
	 * 不归这里管，AOP 框架会在调用此前已经处理好了。
	 *
	 * @param advisor 包含具体 Advice 的切面对象。注意：调用此方法前，必须先通过 {@link #supportsAdvice} 检查通过。
	 * @return 转换后的拦截器对象。Spring 框架拿到这个拦截器后，就会把它加入到方法调用的拦截链中去执行。
	 */
	MethodInterceptor getInterceptor(Advisor advisor);

}
