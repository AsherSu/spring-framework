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

/**
 * 接口，由能够基于 {@link AdvisedSupport} 配置对象创建 AOP 代理的工厂实现。
 *
 * <p>代理对象应遵守以下约定：
 * <ul>
 * <li>它们应该实现配置中指示应该被代理的所有接口。
 * <li>它们应该实现 {@link Advised} 接口。
 * <li>它们应该实现 equals 方法以比较被代理的接口、通知和目标。
 * <li>如果所有通知者和目标都是可序列化的，它们应该是可序列化的。
 * <li>如果通知者和目标都是线程安全的，它们应该是线程安全的。
 * </ul>
 *
 * <p>代理可能允许或不允许更改通知。如果它们不允许更改通知（例如，因为配置已被冻结），则代理应在尝试更改通知时抛出 {@link AopConfigException}。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface AopProxyFactory {

	/**
	 * 根据给定的 AOP 配置创建一个 {@link AopProxy}。
	 * @param config 以 AdvisedSupport 对象形式表示的 AOP 配置
	 * @return 相应的 AOP 代理
	 * @throws AopConfigException 如果配置无效
	 */
	AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException;

}
