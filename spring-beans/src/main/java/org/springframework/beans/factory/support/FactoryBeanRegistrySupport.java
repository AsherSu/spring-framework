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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;

/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/** FactoryBeans 创建的单例对象的缓存：FactoryBean 名称到对象。 */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * Determine the type for the given FactoryBean.
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	protected @Nullable Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			return factoryBean.getObjectType();
		}
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute == null) {
			return ResolvableType.NONE;
		}
		if (attribute instanceof ResolvableType resolvableType) {
			return resolvableType;
		}
		if (attribute instanceof Class<?> clazz) {
			return ResolvableType.forClass(clazz);
		}
		throw new IllegalArgumentException("Invalid value type for attribute '" +
				FactoryBean.OBJECT_TYPE_ATTRIBUTE + "': " + attribute.getClass().getName());
	}

	/**
	 * Determine the FactoryBean object type from the given generic declaration.
	 * @param type the FactoryBean type
	 * @return the nested object type, or {@code NONE} if not resolvable
	 */
	ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		return (type != null ? type.as(FactoryBean.class).getGeneric() : ResolvableType.NONE);
	}

	/**
	 * 从缓存中获取指定 FactoryBean 的对象实例（如果存在）。
	 * 该方法用于快速检查缓存，避免不必要的同步开销。
	 * @param beanName 要查找的 BeanName
	 * @return 缓存中的 FactoryBean 对象，如果缓存中不存在则返回 {@code null}
	 */
	protected @Nullable Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * 从给定的 FactoryBean 中获取要暴露的对象实例。
	 * <p>该方法会根据 FactoryBean 是否为单例 以及 是否已经存在对应的单例实例 来决定如何获取对象：
	 * <ul>
	 *   <li>如果是单例且已存在对应的单例 bean，则尝试从缓存中获取或创建并缓存对象实例。</li>
	 *   <li>如果需要后处理（post-processing），还会对获取到的对象进行后处理操作。</li>
	 *   <li>对于原型（prototype）或其他非单例 FactoryBean，则直接创建新实例并根据需要进行后处理。</li>
	 * </ul>
	 * @param factory FactoryBean 实例
	 * @param requiredType 期望获取的对象类型，可以为 null
	 * @param beanName 规范 beanName
	 * @param shouldPostProcess 是否需要对获取到的对象进行后处理
	 *  BeanPostProcessor.postProcessBeforeInitialization() - 初始化前处理
	 *  BeanPostProcessor.postProcessAfterInitialization() - 初始化后处理
	 * @return 从 FactoryBean 获取到的对象实例
	 * @throws BeanCreationException 如果在创建 FactoryBean 对象时发生错误
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType,
			String beanName, boolean shouldPostProcess) {

		// 判断当前 FactoryBean 是否为单例，且对应的单例 bean 是否已经存在
		if (factory.isSingleton() && containsSingleton(beanName)) {
			// 获取当前线程是否允许持有单例锁的标志
			Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
			boolean locked;
			// 根据 lockFlag 决定是否获取锁
			if (lockFlag == null) {
				// 传统行为：强制获取锁
				this.singletonLock.lock();
				locked = true;
			}
			else {
				// 根据 lockFlag 和 tryLock() 结果决定是否获取锁
				locked = (lockFlag && this.singletonLock.tryLock());
			}
			try {
				// 判断 factory 是否为 SmartFactoryBean 实例
				// SmartFactoryBean 可能返回多种对象类型，因此不进行缓存
				boolean smart = (factory instanceof SmartFactoryBean<?>);
				// 如果不是 SmartFactoryBean，则尝试从缓存中获取对象
				Object object = (!smart ? this.factoryBeanObjectCache.get(beanName) : null);
				// 如果缓存中没有找到对象，则需要创建对象
				if (object == null) {
					// 调用 doGetObjectFromFactoryBean 方法实际创建对象
					object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
					// 再次检查缓存，确保在 doGetObjectFromFactoryBean 调用期间没有其他线程将对象放入缓存
					// (例如，由于自定义 getBean 调用触发的循环引用处理)
					Object alreadyThere = (!smart ? this.factoryBeanObjectCache.get(beanName) : null);
					// 如果缓存中已存在对象，则使用缓存中的对象
					if (alreadyThere != null) {
						object = alreadyThere;
					}
					// 如果缓存中仍然没有对象，则进行后续处理
					else {
						// 如果需要进行后处理
						if (shouldPostProcess) {
							// 如果已获取锁
							if (locked) {
								// 检查当前单例是否正在创建中，如果是，则暂时返回未经过后处理的对象，不将其存储到缓存中
								if (isSingletonCurrentlyInCreation(beanName)) {
									// Temporarily return non-post-processed object, not storing it yet
									return object;
								}
								// 调用 beforeSingletonCreation 方法，标记单例正在创建中
								beforeSingletonCreation(beanName);
							}
							try {
								// 调用 postProcessObjectFromFactoryBean 方法对对象进行后处理
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							// 如果后处理过程中发生异常，则抛出 BeanCreationException
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								// 如果已获取锁，则调用 afterSingletonCreation 方法，标记单例创建完成
								if (locked) {
									afterSingletonCreation(beanName);
								}
							}
						}
						// 如果不是 SmartFactoryBean 且单例 bean 仍然存在，则将对象放入缓存
						if (!smart && containsSingleton(beanName)) {
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				// 返回获取到的对象
				return object;
			}
			finally {
				// 如果已获取锁，则释放锁
				if (locked) {
					this.singletonLock.unlock();
				}
			}
		}
		// 如果不是单例或单例 bean 不存在，则直接创建对象
		else {
			// 调用 doGetObjectFromFactoryBean 方法实际创建对象
			Object object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
			// 如果需要进行后处理
			if (shouldPostProcess) {
				try {
					// 调用 postProcessObjectFromFactoryBean 方法对对象进行后处理
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				// 如果后处理过程中发生异常，则抛出 BeanCreationException
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			// 返回获取到的对象
			return object;
		}
	}

	/**
	 * 从给定的 FactoryBean 中获取要暴露的对象实例。
	 *
	 * <p>该方法会根据 FactoryBean 是否为 SmartFactoryBean 来决定调用哪个 getObject 方法：
	 * <ul>
	 *   <li>如果是 SmartFactoryBean 且指定了 requiredType，则调用带类型的 getObject 方法。</li>
	 *   <li>否则调用无参数的 getObject 方法。</li>
	 * </ul>
	 * <p>如果 FactoryBean 返回 null 且当前正在创建中，则抛出异常；否则返回一个 NullBean 实例。
	 * @param factory FactoryBean 实例
	 * @param requiredType 期望获取的对象类型，可以为 null
	 * @param beanName bean 名称
	 * @return 从 FactoryBean 获取到的对象实例
	 * @throws BeanCreationException 如果在创建 FactoryBean 对象时发生错误
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType, String beanName)
			throws BeanCreationException {

		Object object;
		try {
			// requiredType != null 且 factory 为 SmartFactoryBean ，调用带参数的 getObject 方法。
			object = (requiredType != null && factory instanceof SmartFactoryBean<?> smartFactoryBean ?
					smartFactoryBean.getObject(requiredType) : factory.getObject());
		}
		catch (FactoryBeanNotInitializedException ex) {
			// 如果 FactoryBean 尚未初始化完成，则抛出 BeanCurrentlyInCreationException
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			// 如果在创建对象时发生其他异常，则抛出 BeanCreationException
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// 可能为null的情况：
		// 初始化未完成：FactoryBean在初始化过程中，依赖的资源或属性还未准备就绪
		// 延迟初始化：某些FactoryBean采用延迟初始化策略，在特定条件满足前返回null
		// 条件性创建：基于运行时条件判断是否需要创建对象
		// 资源不可用：依赖的外部资源（如数据库连接、文件等）暂时不可用
		// 循环依赖：在复杂的依赖关系中，可能出现临时的null返回
		if (object == null) {
			// 如果当前单例 bean 正在创建中，则抛出异常
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			// 否则返回一个 NullBean 实例
			object = new NullBean();
		}
		return object;
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * The resulting object will get exposed for bean references.
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return factoryBean;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		super.removeSingleton(beanName);
		this.factoryBeanObjectCache.remove(beanName);
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		super.clearSingletonCache();
		this.factoryBeanObjectCache.clear();
	}

}
