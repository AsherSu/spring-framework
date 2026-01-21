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

package org.springframework.beans.factory;

/**
 * 由希望知道其在 bean 工厂中名称的 beans 实现的接口。
 * 注意通常不推荐一个对象依赖于其 bean 名称，因为这可能导致对外部配置的脆弱依赖，
 * 以及可能的不必要的对 Spring API 的依赖。
 *
 * 有关所有 bean 生命周期方法的列表，请参见
 * BeanFactory BeanFactory javadocs。
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 01.11.2003
 * @see BeanClassLoaderAware
 * @see BeanFactoryAware
 * @see InitializingBean
 */
public interface BeanNameAware extends Aware {

	/**
	 * 设置在创建此 bean 的 bean 工厂中的 bean 的名称。
	 * 此方法在填充常规 bean 属性之后被调用，但在如 InitializingBean#afterPropertiesSet() 这样的
	 * 初始化回调或自定义初始化方法之前被调用。
	 * @param name 工厂中的 bean 的名称。注意，这个名称是工厂中使用的实际 bean 名称，
	 * 这可能与最初指定的名称不同：尤其对于内部 bean 名称，实际的 bean 名称可能已通过添加 "#..." 后缀变得唯一。
	 * 如果需要，可以使用 BeanFactoryUtils#originalBeanName(String) 方法来提取没有后缀的原始 bean 名称。
	 */
	void setBeanName(String name);

}
