/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;

/**
 * 策略接口：用于确定特定的 Bean 定义是否有资格作为特定依赖项的自动装配候选者。
 * * <p>核心作用：它是 Spring IOC 容器的“招聘面试官”。当容器需要为一个属性注入依赖时，
 * 它会拿着“职位描述”（DependencyDescriptor）去问这个解析器，
 * 每一个 Bean（BeanDefinitionHolder）是否符合要求。
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @since 2.5
 */
public interface AutowireCandidateResolver {

	/**
	 * 核心方法：判断给定的 Bean 定义 (bdHolder) 是否有资格注入到 目标依赖 (descriptor) 中。
	 * * <p>原理：
	 * 1. 基础检查：查看 XML/注解中是否设置了 autowire-candidate="false"。
	 * 2. 泛型检查：(在子类实现中) 检查 List<String> 和 List<Integer> 的区别。
	 * 3. @Qualifier 检查：(在子类实现中) 检查名称和限定符注解是否匹配。
	 *
	 * @param bdHolder   Bean 的定义持有者，包含 Bean 名称、别名和 BeanDefinition
	 * @param descriptor 目标注入点（方法参数或字段）的描述符，包含类型、注解等信息
	 * @return 如果该 Bean 有资格作为候选者，则返回 true
	 */
	default boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
		// 默认实现非常简单：只检查 BeanDefinition 本身的 isAutowireCandidate 标记
		return bdHolder.getBeanDefinition().isAutowireCandidate();
	}

	/**
	 * 判断给定的依赖描述符是否是“必须”的 (required)。
	 * * <p>作用：处理 @Autowired(required = false) 的逻辑。
	 * 如果返回 false，当找不到 Bean 时，Spring 不会抛异常，而是注入 null。
	 *
	 * @param descriptor 目标注入点的描述符
	 * @return true 表示必须注入；false 表示找不到也可以忍受
	 * @since 5.0
	 */
	default boolean isRequired(DependencyDescriptor descriptor) {
		return descriptor.isRequired();
	}

	/**
	 * 判断给定的描述符是否声明了除了类型之外的“限定符” (Qualifier)。
	 * * <p>作用：优化判断。如果注入点仅仅是按类型注入 (User user)，没有 @Qualifier，
	 * 那么匹配逻辑会快很多。如果有 @Qualifier，则需要更复杂的元数据比对。
	 *
	 * @param descriptor 目标注入点的描述符
	 * @return 是否包含限定符来进一步缩小候选范围
	 * @since 5.1
	 */
	default boolean hasQualifier(DependencyDescriptor descriptor) {
		return false;
	}

	/**
	 * 确定给定依赖项是否建议了一个目标 Bean 的名称。
	 * * <p>作用：处理简化的 @Qualifier("myBean") 或 JSR-330 的 @Named("myBean")。
	 * 如果这里返回了一个名称，Spring 会尝试直接按这个名字去查找 Bean，而不是海选。
	 *
	 * @param descriptor 目标注入点的描述符
	 * @return 建议的 Bean 名称（如果存在），否则返回 null
	 * @since 6.2
	 */
	default @Nullable String getSuggestedName(DependencyDescriptor descriptor) {
		return null;
	}

	/**
	 * 确定依赖项是否建议了一个具体的值（Default Value）。
	 * * <p>核心作用：处理 @Value 注解。
	 * 当这个方法返回非 null 值（例如 "${app.port}" 字符串）时，
	 * Spring 就会停止寻找 Bean 引用，转而解析这个字符串并进行类型转换，直接注入值。
	 *
	 * @param descriptor 目标注入点的描述符
	 * @return 建议的值（通常是字符串表达式），未找到则返回 null
	 * @since 3.0
	 */
	default @Nullable Object getSuggestedValue(DependencyDescriptor descriptor) {
		return null;
	}

	/**
	 * 如果注入点需要，构建一个用于延迟解析实际依赖项的代理对象。
	 * * <p>核心作用：处理 @Lazy 注解。
	 * 当你在字段上加了 @Lazy @Autowired 时，这个方法会返回一个 CGLIB 或 JDK 动态代理对象。
	 * 这个代理对象仅仅是一个“占位符”，只有当你真正调用它的方法时，它才会去容器里 getBean。
	 *
	 * @param descriptor 目标注入点的描述符
	 * @param beanName   包含该注入点的宿主 Bean 名称
	 * @return 延迟解析的代理对象；如果应该进行直接解析（非延迟），则返回 null
	 * @since 4.0
	 */
	default @Nullable Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
		return null;
	}

	/**
	 * 确定用于延迟解析的代理类（Class），而不是对象实例。
	 * * <p>作用：这是为了支持某些特殊场景（如 AOT 编译或序列化分析），
	 * 即使不实例化对象，也需要知道代理类的类型。
	 *
	 * @since 6.0
	 */
	default @Nullable Class<?> getLazyResolutionProxyClass(DependencyDescriptor descriptor, @Nullable String beanName) {
		return null;
	}

	/**
	 * 如果需要，返回此解析器实例的克隆。
	 * * <p>作用：Spring 的 BeanFactory 配置是可以被复制的。
	 * 当创建一个新的 BeanFactory 并想继承配置时，需要复制这个解析器。
	 * 默认实现是创建一个新实例。子类通常会重写此方法以保留缓存或配置状态。
	 *
	 * @return 解析器的克隆实例，或者是当前实例（如果它是无状态的）
	 * @since 5.2.7
	 */
	default AutowireCandidateResolver cloneIfNecessary() {
		return BeanUtils.instantiateClass(getClass());
	}

}