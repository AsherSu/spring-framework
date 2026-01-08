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

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;

/**
 * The root interface for accessing a Spring bean container.
 *
 * <p>This is the basic client view of a bean container;
 * further interfaces such as {@link ListableBeanFactory} and
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * are available for specific purposes.
 *
 * <p>This interface is implemented by objects that hold a number of bean definitions,
 * each uniquely identified by a String name. Depending on the bean definition,
 * the factory will return either an independent instance of a contained object
 * (the Prototype design pattern), or a single shared instance (a superior
 * alternative to the Singleton design pattern, in which the instance is a
 * singleton in the scope of the factory). Which type of instance will be returned
 * depends on the bean factory configuration: the API is the same. Since Spring
 * 2.0, further scopes are available depending on the concrete application
 * context (for example, "request" and "session" scopes in a web environment).
 *
 * <p>The point of this approach is that the BeanFactory is a central registry
 * of application components, and centralizes configuration of application
 * components (no more do individual objects need to read properties files,
 * for example). See chapters 4 and 11 of "Expert One-on-One J2EE Design and
 * Development" for a discussion of the benefits of this approach.
 *
 * <p>Note that it is generally better to rely on Dependency Injection
 * ("push" configuration) to configure application objects through setters
 * or constructors, rather than use any form of "pull" configuration like a
 * BeanFactory lookup. Spring's Dependency Injection functionality is
 * implemented using this BeanFactory interface and its subinterfaces.
 *
 * <p>Normally a BeanFactory will load bean definitions stored in a configuration
 * source (such as an XML document), and use the {@code org.springframework.beans}
 * package to configure the beans. However, an implementation could simply return
 * Java objects it creates as necessary directly in Java code. There are no
 * constraints on how the definitions could be stored: LDAP, RDBMS, XML,
 * properties file, etc. Implementations are encouraged to support references
 * amongst beans (Dependency Injection).
 *
 * <p>In contrast to the methods in {@link ListableBeanFactory}, all of the
 * operations in this interface will also check parent factories if this is a
 * {@link HierarchicalBeanFactory}. If a bean is not found in this factory instance,
 * the immediate parent factory will be asked. Beans in this factory instance
 * are supposed to override beans of the same name in any parent factory.
 *
 * <p>Bean factory implementations should support the standard bean lifecycle interfaces
 * as far as possible. The full set of initialization methods and their standard order is:
 * <ol>
 * <li>BeanNameAware's {@code setBeanName}
 * <li>BeanClassLoaderAware's {@code setBeanClassLoader}
 * <li>BeanFactoryAware's {@code setBeanFactory}
 * <li>EnvironmentAware's {@code setEnvironment}
 * <li>EmbeddedValueResolverAware's {@code setEmbeddedValueResolver}
 * <li>ResourceLoaderAware's {@code setResourceLoader}
 * (only applicable when running in an application context)
 * <li>ApplicationEventPublisherAware's {@code setApplicationEventPublisher}
 * (only applicable when running in an application context)
 * <li>MessageSourceAware's {@code setMessageSource}
 * (only applicable when running in an application context)
 * <li>ApplicationContextAware's {@code setApplicationContext}
 * (only applicable when running in an application context)
 * <li>ServletContextAware's {@code setServletContext}
 * (only applicable when running in a web application context)
 * <li>{@code postProcessBeforeInitialization} methods of BeanPostProcessors
 * <li>InitializingBean's {@code afterPropertiesSet}
 * <li>a custom {@code init-method} definition
 * <li>{@code postProcessAfterInitialization} methods of BeanPostProcessors
 * </ol>
 *
 * <p>On shutdown of a bean factory, the following lifecycle methods apply:
 * <ol>
 * <li>{@code postProcessBeforeDestruction} methods of DestructionAwareBeanPostProcessors
 * <li>DisposableBean's {@code destroy}
 * <li>a custom {@code destroy-method} definition
 * </ol>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 13 April 2001
 * @see BeanNameAware#setBeanName
 * @see BeanClassLoaderAware#setBeanClassLoader
 * @see BeanFactoryAware#setBeanFactory
 * @see org.springframework.context.EnvironmentAware#setEnvironment
 * @see org.springframework.context.EmbeddedValueResolverAware#setEmbeddedValueResolver
 * @see org.springframework.context.ResourceLoaderAware#setResourceLoader
 * @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher
 * @see org.springframework.context.MessageSourceAware#setMessageSource
 * @see org.springframework.context.ApplicationContextAware#setApplicationContext
 * @see org.springframework.web.context.ServletContextAware#setServletContext
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization
 * @see InitializingBean#afterPropertiesSet
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getInitMethodName
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization
 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor#postProcessBeforeDestruction
 * @see DisposableBean#destroy
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName
 */
public interface BeanFactory {

	/**
	 * 用于将 FactoryBean 和 由FactoryBean创建的bean 区分开。
	 * 例如，如果名为 myJndiObject 的 bean 是 FactoryBean，
	 * 那么获取 &myJndiObject，将返回FactoryBean。
	 * 获取 myJndiObject，将返回FactoryBean创建的对象。
	 */
	String FACTORY_BEAN_PREFIX = "&";
	char FACTORY_BEAN_PREFIX_CHAR = '&';


	/**
	 * 返回指定 bean 的实例，该实例可以是共享的或独立的。
	 * <p>此方法允许将 Spring BeanFactory 用作替代 Singleton 或 Prototype 设计模式。
	 * 在单例 bean 的情况下，调用者可以保留对返回对象的引用。
	 * <p>将别名转换回相应的规范 bean 名称。
	 * <p>如果在此工厂实例中找不到 bean，则将询问父工厂。
	 * @param name 要检索的 bean 的名称
	 * @return bean 的实例
	 * @throws NoSuchBeanDefinitionException 如果没有具有指定名称的 bean
	 * @throws BeansException 如果无法获取 bean
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * 返回指定 bean 的实例，该实例可以是共享的或独立的。
	 * <p>与 {@link #getBean(String)} 行为相同，但通过抛出 BeanNotOfRequiredTypeException
	 * 来提供类型安全性。这意味着与 {@link #getBean(String)} 可能发生的正确类型转换上不会抛出 ClassCastException。
	 * <p>将别名转换回相应的规范 bean 名称。
	 * <p>如果在此工厂实例中找不到 bean，则将询问父工厂。
	 * @param name 要检索的 bean 的名称
	 * @param requiredType bean 必须匹配的类型；可以是接口或超类
	 * @return bean 的实例
	 * @throws NoSuchBeanDefinitionException 如果没有这样的 bean 定义
	 * @throws BeanNotOfRequiredTypeException 如果 bean 不是所需类型
	 * @throws BeansException 如果无法创建 bean
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * 返回指定 bean 的实例，该实例可以是共享的或独立的。
	 * <p>允许指定显式构造函数参数/工厂方法参数，覆盖 bean 定义中指定的默认参数（如果有）。
	 * @param name 要检索的 bean 的名称
	 * @param args 在使用显式参数创建 bean 实例时使用的参数（仅在创建新实例而不是检索现有实例时应用）
	 * @return bean 的实例
	 * @throws NoSuchBeanDefinitionException 如果没有这样的 bean 定义
	 * @throws BeanDefinitionStoreException 如果给定参数但受影响的 bean 不是原型
	 * @throws BeansException 如果无法创建 bean
	 * @since 2.5
	 */
	Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException;

	/**
	 * 返回与给定对象类型唯一匹配的 bean 实例（如果存在）。
	 * <p>此方法进入 {@link ListableBeanFactory} 按类型查找领域，
	 * 但也可以根据给定类型的名称将其转换为传统的按名称查找。
	 * 要在一组 bean 中执行更广泛的检索操作，请使用 {@link ListableBeanFactory} 和/或 {@link BeanFactoryUtils}。
	 * @param requiredType bean 必须匹配的类型；可以是接口或超类
	 * @return 匹配所需类型的单个 bean 的实例
	 * @throws NoSuchBeanDefinitionException 如果没有找到给定类型的 bean
	 * @throws NoUniqueBeanDefinitionException 如果找到给定类型的多个 bean
	 * @throws BeansException 如果无法创建 bean
	 * @since 3.0
	 * @see ListableBeanFactory
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * 返回指定 bean 的实例，该实例可以是共享的或独立的。
	 * <p>允许指定显式构造函数参数/工厂方法参数，覆盖 bean 定义中指定的默认参数（如果有）。
	 * <p>此方法进入 {@link ListableBeanFactory} 按类型查找领域，
	 * 但也可以根据给定类型的名称将其转换为传统的按名称查找。
	 * 要在一组 bean 中执行更广泛的检索操作，请使用 {@link ListableBeanFactory} 和/或 {@link BeanFactoryUtils}。
	 * @param requiredType bean 必须匹配的类型；可以是接口或超类
	 * @param args 在使用显式参数创建 bean 实例时使用的参数
	 * （仅在创建新实例而不是检索现有实例时应用）
	 * @return bean 的实例
	 * @throws NoSuchBeanDefinitionException 如果没有这样的 bean 定义
	 * @throws BeanDefinitionStoreException 如果给定参数但受影响的 bean 不是原型
	 * @throws BeansException 如果无法创建 bean
	 * @since 4.1
	 */
	<T> T getBean(Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException;

	/**
	 * 返回指定 bean 的提供程序，允许进行延迟的按需检索实例，包括可用性和唯一性选项。
	 * @param requiredType bean 必须匹配的类型；可以是接口或超类
	 * @return 相应的提供程序句柄
	 * @since 5.1
	 * @see #getBeanProvider(ResolvableType)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * 返回指定 bean 的提供程序，允许进行延迟的按需检索实例，包括可用性和唯一性选项。
	 * @param requiredType bean 必须匹配的类型；可以是泛型类型声明。
	 * 请注意，此处不支持集合类型，与反射注入点形成对比。要以编程方式检索与特定类型匹配的 bean 列表，请在此处指定实际 bean 类型，
	 * 然后随后使用 {@link ObjectProvider#orderedStream()} 或其惰性流/迭代选项。
	 * @return 相应的提供程序句柄
	 * @since 5.1
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * 此 bean 工厂是否包含具有给定名称的 bean 定义或外部注册的单例实例？
	 * <p>如果给定的名称是一个别名，它将被转换回相应的规范 bean 名称。
	 * <p>如果此工厂是分层的，如果在此工厂实例中找不到 bean，则将询问任何父工厂。
	 * <p>如果找到与给定名称匹配的 bean 定义或单例实例，
	 * 无论指定的 bean 定义是具体的还是抽象的，是懒加载的还是急加载的，是否在范围内，此方法都将返回 {@code true}。
	 * 因此，请注意，此方法的 {@code true} 返回值不一定表示 {@link #getBean}
	 * 将能够为相同名称获取实例。
	 * @param name 要查询的 bean 的名称
	 * @return 是否存在具有给定名称的 bean
	 */
	boolean containsBean(String name);

	/**
	 * 此 bean 是否为共享单例？也就是说，{@link #getBean} 是否始终返回相同的实例？
	 * <p>注意：此方法返回 {@code false} 不清楚地指示独立的实例。
	 * 它表示非单例实例，这可能对应于作用域 bean。使用 {@link #isPrototype} 操作明确检查独立实例。
	 * <p>将别名转换回相应的规范 bean 名称。
	 * <p>如果在此工厂实例中找不到 bean，则将询问父工厂。
	 * @param name 要查询的 bean 的名称
	 * @return 此 bean 是否对应于单例实例
	 * @throws NoSuchBeanDefinitionException 如果没有具有给定名称的 bean
	 * @see #getBean
	 * @see #isPrototype
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Is this bean a prototype? That is, will {@link #getBean} always return
	 * independent instances?
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * a singleton object. It indicates non-independent instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isSingleton} operation to explicitly
	 * check for a shared singleton instance.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return whether this bean will always deliver independent instances
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.3
	 * @see #getBean
	 * @see #isSingleton
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code ResolvableType})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 4.2
	 * @see #getBean
	 * @see #getType
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code Class})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.1
	 * @see #getBean
	 * @see #getType
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. This may lead to the initialization
	 * of a previously uninitialized {@code FactoryBean} (see {@link #getType(String, boolean)}).
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 1.1.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 确定具有给定名称的 bean 的类型。
	 * 更具体地说，确定 {@link #getBean} 对于给定名称将返回的对象的类型。
	 * <p>对于 {@link FactoryBean}，返回 FactoryBean 创建的对象的类型，由 {@link FactoryBean#getObjectType()} 公开。
	 * 根据 {@code allowFactoryBeanInit} 标志的不同，如果没有提供早期类型信息，则可能导致以前未初始化的 {@code FactoryBean} 的初始化。
	 * <p>将别名转换回相应的规范 bean 名称。
	 * <p>如果在此工厂实例中找不到 bean，则将询问父工厂。
	 * @param name 要查询的 bean 的名称
	 * @param allowFactoryBeanInit 是否可能为了确定其对象类型而初始化 {@code FactoryBean}
	 * @return bean 的类型，如果无法确定则为 {@code null}
	 * @throws NoSuchBeanDefinitionException 如果没有具有给定名称的 bean
	 * @since 5.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * 返回给定 bean 名称的别名，如果有的话。
	 * <p>在 {@link #getBean} 调用中，所有这些别名都指向相同的 bean。
	 * <p>如果给定的名称是别名，则将返回相应的原始 bean 名称和其他别名（如果有的话），
	 * 原始 bean 名称将是数组中的第一个元素。
	 * <p>如果在此工厂实例中找不到 bean，则将询问父工厂。
	 * @param name 要检查别名的 bean 名称
	 * @return 别名，如果没有则为空数组
	 * @see #getBean
	 */
	String[] getAliases(String name);

}
