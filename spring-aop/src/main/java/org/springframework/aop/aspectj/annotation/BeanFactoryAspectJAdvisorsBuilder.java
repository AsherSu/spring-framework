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

package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.reflect.PerClauseKind;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.Assert;

/**
 * Helper for retrieving @AspectJ beans from a BeanFactory and building
 * Spring Advisors based on them, for use with auto-proxying.
 *
 * @author Juergen Hoeller
 * @since 2.0.2
 * @see AnnotationAwareAspectJAutoProxyCreator
 */
public class BeanFactoryAspectJAdvisorsBuilder {

	private static final Log logger = LogFactory.getLog(BeanFactoryAspectJAdvisorsBuilder.class);

	private final ListableBeanFactory beanFactory;

	private final AspectJAdvisorFactory advisorFactory;

	private volatile @Nullable List<String> aspectBeanNames;

	private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();

	private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();


	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
		this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
	}

	/**
	 * Create a new BeanFactoryAspectJAdvisorsBuilder for the given BeanFactory.
	 * @param beanFactory the ListableBeanFactory to scan
	 * @param advisorFactory the AspectJAdvisorFactory to build each Advisor with
	 */
	public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
		Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
		Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
		this.beanFactory = beanFactory;
		this.advisorFactory = advisorFactory;
	}

	/**
	 * 在当前 BeanFactory 中查找 AspectJ 注解的切面 Bean，
	 * 并返回代表它们的 Spring AOP Advisors 列表。
	 * <p>为每个 AspectJ 通知方法（Advice method）创建一个 Spring Advisor。
	 * @return {@link org.springframework.aop.Advisor} beans 的列表
	 * @see #isEligibleBean
	 */
	public List<Advisor> buildAspectJAdvisors() {
		// 获取缓存的切面 Bean 名称列表
		List<String> aspectNames = this.aspectBeanNames;

		// 1. 如果 aspectNames 为 null，说明是第一次访问，需要执行查找和解析逻辑（Double-check locking 模式）
		if (aspectNames == null) {
			synchronized (this) {
				aspectNames = this.aspectBeanNames;
				if (aspectNames == null) {
					List<Advisor> advisors = new ArrayList<>();
					aspectNames = new ArrayList<>();

					// 获取当前 BeanFactory 中所有的 Bean 名称（包括祖先工厂中的）
					// 传入 Object.class 表示获取所有类型的 Bean
					String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(
							this.beanFactory, Object.class, true, false);

					// 遍历所有 Bean 名称，寻找标注了 @Aspect 的 Bean
					for (String beanName : beanNames) {
						// 检查该 Bean 是否符合自动代理的资格（例如根据 includePatterns 过滤，或者排除某些内部 Bean）
						if (!isEligibleBean(beanName)) {
							continue;
						}

						// 我们必须小心，不要在这里急切地实例化 Bean (Eager Instantiation)。
						// 如果在这里实例化，它们会被 Spring 容器缓存，但此时 AOP 还没准备好，导致这些 Bean 无法被织入代理。
						// 因此这里只获取 Bean 的类型 (Type)，而不触发 getBean()。
						Class<?> beanType = this.beanFactory.getType(beanName, false);
						if (beanType == null) {
							continue;
						}

						// 判断该类是否标注了 @Aspect 注解，且不是由 ajc 编译的
						if (this.advisorFactory.isAspect(beanType)) {
							try {
								AspectMetadata amd = new AspectMetadata(beanType, beanName);

								// 判断 @Aspect 的实例化模型 (Instantiation Model)
								// 大多数情况下是 SINGLETON (默认)，即单例切面
								if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
									MetadataAwareAspectInstanceFactory factory =
											new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);

									// 核心逻辑：利用 advisorFactory 解析切面类中的增强方法（@Before, @After 等），
									// 将它们转换为 List<Advisor>
									List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
									if (this.beanFactory.isSingleton(beanName)) {
										this.advisorsCache.put(beanName, classAdvisors);
									}
									// 如果 Bean 是单例的，则直接缓存解析好的 Advisors
									else {
										this.aspectFactoryCache.put(beanName, factory);
									}
									advisors.addAll(classAdvisors);
								}
								else {
									// 处理 Per-target 或 Per-this 的切面（较少见）
									// 这种类型的切面 Bean 不能是单例的
									if (this.beanFactory.isSingleton(beanName)) {
										throw new IllegalArgumentException("Bean with name '" + beanName +
												"' is a singleton, but aspect instantiation model is not singleton");
									}
									MetadataAwareAspectInstanceFactory factory =
											new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
									this.aspectFactoryCache.put(beanName, factory);
									advisors.addAll(this.advisorFactory.getAdvisors(factory));
								}
								// 将确认为切面的 Bean 名称加入列表
								aspectNames.add(beanName);
							}
							catch (IllegalArgumentException | IllegalStateException | AopConfigException ex) {
								if (logger.isDebugEnabled()) {
									logger.debug("Ignoring incompatible aspect [" + beanType.getName() + "]: " + ex);
								}
							}
						}
					}
					// 将找到的切面名称列表赋值给成员变量（缓存起来，下次不再重复扫描）
					this.aspectBeanNames = aspectNames;
					return advisors;
				}
			}
		}

		// 2. 如果 aspectNames 不为 null，说明之前已经解析过了，直接从缓存中获取
		if (aspectNames.isEmpty()) {
			return Collections.emptyList();
		}
		List<Advisor> advisors = new ArrayList<>();
		for (String aspectName : aspectNames) {
			// 尝试从缓存中获取已经构建好的 Advisors
			List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
			if (cachedAdvisors != null) {
				advisors.addAll(cachedAdvisors);
			}
			else {
				// 如果缓存中没有（通常是 Prototype 类型的切面），则从工厂缓存中获取工厂，重新构建 Advisors
				MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
				Assert.state(factory != null, "Factory must not be null");
				advisors.addAll(this.advisorFactory.getAdvisors(factory));
			}
		}
		return advisors;
	}

	/**
	 * Return whether the aspect bean with the given name is eligible.
	 * @param beanName the name of the aspect bean
	 * @return whether the bean is eligible
	 */
	protected boolean isEligibleBean(String beanName) {
		return true;
	}

}
