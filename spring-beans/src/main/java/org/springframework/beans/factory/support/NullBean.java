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

package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.FactoryBean;

/**
* null bean 实例的内部表示，例如，对于 {@code null} 值
 * 从 {@link FactoryBean#getObject（）} 或从工厂方法返回。
 *
 * <p>每个这样的 null bean 都由一个专用的 {@code NullBean} 实例表示
 * 它们彼此不相等，唯一区分返回的每个 bean
 * 来自 {@link org.springframework.beans.factory.BeanFactory#getBean} 的所有变体。
 * 但是，每个这样的实例都会为 {@code #equals（null）} 返回 {@code true}
 * 并从 {@code #toString（）} 返回“null”，这就是测试它们的方式
 * 外部（因为这个类本身不是公共的）。
 *
 * @author Juergen Hoeller
 * @since 5.0
 */
final class NullBean {

	NullBean() {
	}


	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || other == null);
	}

	@Override
	public int hashCode() {
		return NullBean.class.hashCode();
	}

	@Override
	public String toString() {
		return "null";
	}

}
