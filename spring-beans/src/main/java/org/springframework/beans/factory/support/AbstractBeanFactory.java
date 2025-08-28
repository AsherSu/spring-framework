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

import java.beans.PropertyEditor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** BeanFactory 的 parentBeanFactory，用于实现 Bean 工厂的层次结构支持 */
	private @Nullable BeanFactory parentBeanFactory;

	/** ClassLoader to resolve bean class names with, if necessary. */
	private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** ClassLoader to temporarily resolve bean class names with, if necessary. */
	private @Nullable ClassLoader tempClassLoader;

	/** 是否缓存 bean 元数据。 Bean Metadata：Bean 自身之外 的各种声明式信息（作用域、依赖、来源、注解等）*/
	private boolean cacheBeanMetadata = true;

	/** Resolution strategy for expressions in bean definition values. */
	private @Nullable BeanExpressionResolver beanExpressionResolver;

	/** Spring ConversionService to use instead of PropertyEditors. */
	private @Nullable ConversionService conversionService;

	/** Default PropertyEditorRegistrars to apply to the beans of this factory. */
	private final Set<PropertyEditorRegistrar> defaultEditorRegistrars = new LinkedHashSet<>(4);

	/** Custom PropertyEditorRegistrars to apply to the beans of this factory. */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/** Custom PropertyEditors to apply to the beans of this factory. */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** typeConverter 是一个自定义的类型转换器，用于在Bean创建和属性注入过程中进行类型转换。它可以覆盖Spring默认的PropertyEditor机制 */
	private @Nullable TypeConverter typeConverter;

	/** 字符串解析器列表，用于解析字符串值，例如注解属性值。
	 * 占位符解析器 (Placeholder Resolvers)
	 * 	- 解析 ${property.name} 形式的属性占位符，从配置文件、环境变量、系统属性中获取值
	 * SpEL表达式解析器 (SpEL Expression Resolvers)
	 * 	- 解析 #{expression} 形式的Spring表达式语言，支持复杂的表达式计算和Bean引用
	 * 注解属性值解析器
	 * 	- 解析注解中的字符串属性值，如 @Value("${config.value}") 中的占位符
	 * 环境变量解析器
	 * 	- 解析环境变量和系统属性，支持默认值设置
	 */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/** 存储和管理beanPostProcessor的集合.
	 *
	 * Bean生命周期增强: BeanPostProcessor可以在Bean初始化前后执行自定义逻辑
	 * AOP代理创建: 许多AOP实现通过BeanPostProcessor创建代理对象
	 * 依赖注入: 如@Autowired、@Resource等注解的处理
	 * 属性填充: 在Bean属性设置阶段进行干预
	 */
	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	/** Cache of pre-filtered post-processors. */
	private @Nullable BeanPostProcessorCache beanPostProcessorCache;

	/** Map from scope identifier String to corresponding Scope. */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/** Application startup metrics. */
	private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;

	/** beanName -> 合并的 RootBeanDefinition。 */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/** Names of beans that have already been created at least once. */
	private final Set<String> alreadyCreated = ConcurrentHashMap.newKeySet(256);

	/** 当前正在创建的 Bean 的名称。 */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object @Nullable ... args)
			throws BeansException {

		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * 返回指定 bean 的实例，该���例可以是共享的，也可以是独立的。
	 * @param name 命名要检索的 bean 的名称
	 * @param requiredType required键入要检索的 Bean 的所需类型
	 * @param args 使用显式参数创建 bean 实例时要使用的参数
	 *（仅在创建新实例而不是检索现有实例时应用）
	 * @param typeCheckOnly 是否为类型检查获取实例，
	 * 不用于实际使用
	 * @return bean 的实例
	 * @throws BeansException，如果无法创建 bean
	 */
	//1. 缓存单例分支
	//   - 条件：`sharedInstance != null && args == null`
	//   - 返回：读取已缓存的单例或早期引用并调用 `getObjectForBeanInstance`
	//
	//2. 原型循环检测分支
	//   - 条件：`isPrototypeCurrentlyInCreation(beanName)`
	//   - 返回：抛出 `BeanCurrentlyInCreationException`
	//
	//3. 委托父工厂分支
	//   - 条件：`parentBeanFactory != null && !containsBeanDefinition(beanName)`
	//   - 返回：调用父工厂的 `doGetBean` 或 `getBean`
	//
	//4. 本地创建分支
	//   - 标记创建：`!typeCheckOnly` 时执行 `markBeanAsCreated`
	//   - 加载合并定义：`getMergedLocalBeanDefinition`
	//   - 根据作用域创建：
	//     - 单例（`mbd.isSingleton()`）
	//     - 原型（`mbd.isPrototype()`）
	//     - 自定义作用域（其它 `scope`）
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object @Nullable [] args, boolean typeCheckOnly)
			throws BeansException {

		// 从别名映射到规范命名
		String beanName = transformedBeanName(name);
		Object beanInstance;

		// 提前检查 单例缓存 中是否有手动注册的单例。 返回单例bean或者早期bean
		Object sharedInstance = getSingleton(beanName);

		// 在单例缓存里已经有一个实例 同时 调用方没有提供额外的构造参数，则复用
		if (sharedInstance != null && args == null) {
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			beanInstance = getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, null);
		}

		// bean不存在，需要创建bean
		else {
			// 原型模式产生循环依赖的情况
			// 1. 直接自引用：Bean A 在构造函数或属性中直接依赖自己
			// 2. 构造函数循环依赖：两个或多个原型 Bean 通过构造函数相互依赖
			// 3. 工厂方法循环依赖：通过 @Bean 方法创建的原型 Bean 之间的循环依赖
			// 4. 多层级循环依赖：A → B → C → A 的循环依赖链
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			BeanFactory parentBeanFactory = getParentBeanFactory();
			// 有父工厂 且 当前工厂没有该bd，让父工厂创建bean
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				String nameToLookup = originalBeanName(name);
				if (parentBeanFactory instanceof AbstractBeanFactory abf) {
					return abf.doGetBean(nameToLookup, requiredType, args, typeCheckOnly);
				}
				else if (args != null) {
					// Delegation to parent with explicit args.
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			// 如果 typeCheckOnly 为 true，表示只是为了类型匹配或依赖检查，不会真正创建或返回 bean 实例。
			// 如果为 false，则表示要实际获取并使用该 bean。
			if (!typeCheckOnly) {
				// 标记创建bean时需要重新合并beanDefinition
				markBeanAsCreated(beanName);
			}

			StartupStep beanCreation = this.applicationStartup.start("spring.beans.instantiate")
					.tag("beanName", name);
			try {
				if (requiredType != null) {
					beanCreation.tag("beanType", requiredType::toString);
				}
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				checkMergedBeanDefinition(mbd, beanName, args);

				// 保证当前 Bean 所依赖的 Bean 被先初始化。
				String[] dependsOn = mbd.getDependsOn();
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						// 检测循环依赖，如果 dep->beanName 则存在循环依赖
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注册依赖关系
						registerDependentBean(dep, beanName);
						try {
							// 初始化依赖的bean
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
						catch (BeanCreationException ex) {
							if (requiredType != null) {
								// Wrap exception with current bean metadata but only if specifically
								// requested (indicated by required type), not for depends-on cascades.
								throw new BeanCreationException(mbd.getResourceDescription(), beanName,
										"Failed to initialize dependency '" + ex.getBeanName() + "' of " +
												requiredType.getSimpleName() + " bean '" + beanName + "': " +
												ex.getMessage(), ex);
							}
							throw ex;
						}
					}
				}

				// 开始创建 Bean 实例
				if (mbd.isSingleton()) {
					// 控制创建bean的线程安全
					sharedInstance = getSingleton(beanName, () -> {
						try {
							// 创建bean的核心方法
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							destroySingleton(beanName);
							throw ex;
						}
					});
					beanInstance = getObjectForBeanInstance(sharedInstance, requiredType, name, beanName, mbd);
				}

				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					Object prototypeInstance = null;
					try {
						beforePrototypeCreation(beanName);
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						afterPrototypeCreation(beanName);
					}
					beanInstance = getObjectForBeanInstance(prototypeInstance, requiredType, name, beanName, mbd);
				}

				else {
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean '" + beanName + "'");
					}
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							beforePrototypeCreation(beanName);
							try {
								return createBean(beanName, mbd, args);
							}
							finally {
								afterPrototypeCreation(beanName);
							}
						});
						beanInstance = getObjectForBeanInstance(scopedInstance, requiredType, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			}
			catch (BeansException ex) {
				beanCreation.tag("exception", ex.getClass().toString());
				beanCreation.tag("message", String.valueOf(ex.getMessage()));
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
			finally {
				beanCreation.end();
				if (!isCacheBeanMetadata()) {
					clearMergedBeanDefinition(beanName);
				}
			}
		}

		return adaptBeanInstance(name, beanInstance, requiredType);
	}

	/**
	 * 适配Bean实例到所需的类型
	 * 这是Spring类型转换系统的一个重要组成部分，确保获取的Bean实例符合调用者期望的类型
	 *
	 * 主要功能：
	 * 1. 检查Bean实例是否已经是所需类型
	 * 2. 如果类型不匹配，尝试使用类型转换器进行转换
	 * 3. 处理转换失败的情况，提供清晰的错误信息
	 *
	 * 常见使用场景：
	 * - 当通过@Autowired注入时需要特定类型
	 * - 通过getBean(String, Class)方法获取特定类型的Bean
	 * - FactoryBean返回的对象需要转换为目标类型
	 * - 代理对象需要转换为目标接口类型
	 *
	 * @param name Bean的名称，用于错误信息中标识具体的Bean
	 * @param bean 实际的Bean实例，可能需要进行类型转换
	 * @param requiredType 调用者期望的目标类型，如果为null则直接返回原始Bean
	 * @return 适配后的Bean实例，保证是requiredType类型或其子类型
	 * @throws BeanNotOfRequiredTypeException 当Bean无法转换为所需类型时抛出
	 */
	@SuppressWarnings("unchecked")
	<T> T adaptBeanInstance(String name, Object bean, @Nullable Class<?> requiredType) {
		// 第一步：类型兼容性检查
		// 如果没有指定所需类型，或者Bean实例已经是所需类型的实例，则直接返回
		// isInstance()方法会检查继承关系和接口实现关系
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				// 第二步：尝试类型转换
				// 使用Spring的类型转换系统尝试将Bean转换为所需类型
				// TypeConverter支持多种转换方式：
				// - 基本类型转换（如String到Integer）
				// - 集合类型转换（如List到Set）
				// - 自定义PropertyEditor转换
				// - ConversionService转换
				Object convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);

				// 第三步：转换结果验证
				// 如果转换器返回null，说明无法进行转换
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}

				// 转换成功，返回转换后的对象
				return (T) convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}

		// Bean已经是所需类型，或者没有指定所需类型，直接返回原始Bean
		// 这里的强制类型转换是安全的，因为上面的检查已经确保了类型兼容性
		return (T) bean;
	}

	/**
	 * 检查工厂中是否包含具有给定名称的 bean 实例或定义。
	 * <p>如果给定名称是别名，它将被转换回相应的规范 bean 名称。
	 * <p>如果此工厂是分层的，将向上询问任何父工厂（如果在此工厂实例中找不到该 bean）。
	 * <p>如果找到与给定名称匹配的 bean 定义或单例实例，此方法将返回 {@code true}，
	 * 无论命名的 bean 定义是具体的还是抽象的、延迟加载还是急切加载、作用域内还是不在作用域内。
	 * 因此，请注意，{@code true} 的返回值不一定表示 {@link #getBean} 能够为同一名称获取实例。
	 * @param name 要查询的 bean 的名称
	 * @return 是否存在具有给定名称的 bean
	 */
	@Override
	public boolean containsBean(String name) {
		// 转换 bean 名称，如果以 & 开头则去除前缀获取真实的 beanName
		String beanName = transformedBeanName(name);
		
		// 检查当前工厂是否包含该 bean 实例或定义
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			// 如果 name 是工厂引用（以 & 开头），则检查对应的 bean 是否为 FactoryBean
			// 否则直接返回 true 表示找到了对应的 bean
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		
		// 当前工厂未找到 -> 检查父工厂
		BeanFactory parentBeanFactory = getParentBeanFactory();
		// 如果存在父工厂，则委托给父工厂进行检查
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean<?> factoryBean) {
				return (BeanFactoryUtils.isFactoryDereference(name) || factoryBean.isSingleton());
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			return ((fb instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isPrototype()) ||
					!fb.isSingleton());
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		String beanName = transformedBeanName(name);
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {

			// Determine target for FactoryBean match if necessary.
			if (beanInstance instanceof FactoryBean<?> factoryBean) {
				if (!isFactoryDereference) {
					if (factoryBean instanceof SmartFactoryBean<?> smartFactoryBean &&
							smartFactoryBean.supportsType(typeToMatch.toClass())) {
						return true;
					}
					Class<?> type = getTypeForFactoryBean(factoryBean);
					if (type == null) {
						return false;
					}
					if (typeToMatch.isAssignableFrom(type)) {
						return true;
					}
					else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
						RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
						ResolvableType targetType = mbd.targetType;
						if (targetType == null) {
							targetType = mbd.factoryMethodReturnType;
						}
						if (targetType == null) {
							return false;
						}
						Class<?> targetClass = targetType.resolve();
						if (targetClass != null && FactoryBean.class.isAssignableFrom(targetClass)) {
							Class<?> classToMatch = typeToMatch.resolve();
							if (classToMatch != null && !FactoryBean.class.isAssignableFrom(classToMatch) &&
									!classToMatch.isAssignableFrom(targetType.toClass())) {
								return typeToMatch.isAssignableFrom(targetType.getGeneric());
							}
						}
						else {
							return typeToMatch.isAssignableFrom(targetType);
						}
					}
					return false;
				}
			}
			else if (isFactoryDereference) {
				return false;
			}

			// Actual matching against bean instance...
			if (typeToMatch.isInstance(beanInstance)) {
				// Direct match for exposed instance?
				return true;
			}
			else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
				// Generics potentially only match on the target class, not on the proxy...
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				Class<?> targetType = mbd.getTargetType();
				if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
					// Check raw class match as well, making sure it's exposed on the proxy.
					Class<?> classToMatch = typeToMatch.resolve();
					if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
						return false;
					}
					if (typeToMatch.isAssignableFrom(targetType)) {
						return true;
					}
				}
				ResolvableType resolvableType = mbd.targetType;
				if (resolvableType == null) {
					resolvableType = mbd.factoryMethodReturnType;
				}
				return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
			}
			else {
				return false;
			}
		}
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			return false;
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Set up the types that we want to match against
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});

		// Attempt to predict the bean type
		Class<?> predictedType = null;

		// We're looking for a regular reference, but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type, but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	public @Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	@Override
	public @Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean(factoryBean);
			}
			else {
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.getType(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		Class<?> beanClass = predictBeanType(beanName, mbd);

		if (beanClass != null) {
			// Check bean class whether we're dealing with a FactoryBean.
			if (FactoryBean.class.isAssignableFrom(beanClass)) {
				if (!BeanFactoryUtils.isFactoryDereference(name)) {
					// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
					beanClass = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
				}
			}
			else if (BeanFactoryUtils.isFactoryDereference(name)) {
				return null;
			}
		}

		if (beanClass == null) {
			// Check decorated bean definition, if any: We assume it'll be easier
			// to determine the decorated bean's type than the proxy's type.
			BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
			if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
				if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
					return targetClass;
				}
			}
		}

		return beanClass;
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		boolean hasFactoryPrefix = (!name.isEmpty() && name.charAt(0) == BeanFactory.FACTORY_BEAN_PREFIX_CHAR);
		String fullBeanName = beanName;
		if (hasFactoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = (hasFactoryPrefix ? FACTORY_BEAN_PREFIX : "");
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public @Nullable BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	public @Nullable ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	public @Nullable ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	public @Nullable BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	public @Nullable ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		if (registrar.overridesDefaultEditors()) {
			this.defaultEditorRegistrars.add(registrar);
		}
		else {
			this.propertyEditorRegistrars.add(registrar);
		}
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * 返回要使用的自定义 TypeConverter（如果有）。
	 * @return 自定义 TypeConverter，如果未指定则返回 {@code null}
	 */
	protected @Nullable TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	@Override
	public @Nullable String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			result = resolver.resolveStringValue(result);
			if (result == null) {
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			this.beanPostProcessors.remove(beanPostProcessor);
			// Add to end of list
			this.beanPostProcessors.add(beanPostProcessor);
		}
	}

	/**
	 * Add new BeanPostProcessors that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * @since 5.3
	 * @see #addBeanPostProcessor
	 */
	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		synchronized (this.beanPostProcessors) {
			// Remove from old position, if any
			this.beanPostProcessors.removeAll(beanPostProcessors);
			// Add to end of list
			this.beanPostProcessors.addAll(beanPostProcessors);
		}
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * 返回预过滤 BeanPostProcessor 的内部缓存，如有必要，将重新构建它。（减少 类型检查instanceof + 强制转换）
	 *
	 * 这个方法是Spring框架中的性能优化机制，通过缓存不同类型的BeanPostProcessor
	 * 来避免在Bean生命周期的各个阶段重复进行类型检查和转换操作。
	 *
	 * 缓存的BeanPostProcessor类型包括：
	 * - InstantiationAware: 每个Bean创建时都要检查是否需要自定义实例化
	 * - SmartInstantiationAware: AOP代理创建、构造函数推断时频繁使用
	 * - DestructionAware: 单例Bean销毁时必须调用
	 * - MergedBeanDefinition: 每次合并Bean定义时都需要
	 *
	 * @return BeanPostProcessor缓存对象，包含按类型分类的处理器列表
	 * @since 5.3
	 */
	BeanPostProcessorCache getBeanPostProcessorCache() {
		// 使用synchronized确保多线程环境下的线程安全
		// 以beanPostProcessors集合作为锁对象，避免在构建缓存过程中集合被修改
		synchronized (this.beanPostProcessors) {
			BeanPostProcessorCache bppCache = this.beanPostProcessorCache;

			// 懒加载机制 - 只有在缓存为null时才重新构建
			if (bppCache == null) {
				bppCache = new BeanPostProcessorCache();

				// 第四步：遍历所有已注册的BeanPostProcessor进行分类缓存
				for (BeanPostProcessor bpp : this.beanPostProcessors) {
					// 实例化感知处理器
					if (bpp instanceof InstantiationAwareBeanPostProcessor instantiationAwareBpp) {
						// 添加到实例化感知处理器缓存列表
						bppCache.instantiationAware.add(instantiationAwareBpp);

						// 嵌套类型检查：智能实例化感知处理器
						if (bpp instanceof SmartInstantiationAwareBeanPostProcessor smartInstantiationAwareBpp) {
							// 添加到智能实例化感知处理器缓存列表
							bppCache.smartInstantiationAware.add(smartInstantiationAwareBpp);
						}
					}

					// 销毁感知处理器，用于处理Bean销毁前的清理逻辑
					if (bpp instanceof DestructionAwareBeanPostProcessor destructionAwareBpp) {
						// 添加到销毁感知处理器缓存列表
						bppCache.destructionAware.add(destructionAwareBpp);
					}

					// 合并Bean定义处理器，用于处理Bean定义合并后的后置处理逻辑
					if (bpp instanceof MergedBeanDefinitionPostProcessor mergedBeanDefBpp) {
						// 添加到合并Bean定义处理器缓存列表
						bppCache.mergedDefinition.add(mergedBeanDefBpp);
					}
				}

				// 将构建完成的缓存设置到实例变量中
				this.beanPostProcessorCache = bppCache;
			}

			// 返回缓存对象，如果缓存已存在则直接返回，如果不存在则返回刚构建的缓存
			return bppCache;
		}
	}


	private void resetBeanPostProcessorCache() {
		synchronized (this.beanPostProcessors) {
			this.beanPostProcessorCache = null;
		}
	}

	/**
	 * 检查当前 Bean 工厂是否包含 InstantiationAwareBeanPostProcessor 类型的处理器，这些处理器会在创建单例 Bean 时被应用。
	 *
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	public @Nullable Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	@Override
	public void setApplicationStartup(ApplicationStartup applicationStartup) {
		Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
		this.applicationStartup = applicationStartup;
	}

	@Override
	public ApplicationStartup getApplicationStartup() {
		return this.applicationStartup;
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory otherAbstractFactory) {
			this.defaultEditorRegistrars.addAll(otherAbstractFactory.defaultEditorRegistrars);
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		String beanName = transformedBeanName(name);
		// 如果当前 beanFactory 没有该 bean，则去 父beanFactory 寻找
		if (getParentBeanFactory() instanceof ConfigurableBeanFactory parent && !containsBeanDefinition(beanName)) {
			return parent.getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		return getMergedLocalBeanDefinition(beanName);
	}

	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);
		}
		// No singleton instance found -> check bean definition.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory cbf) {
			// No bean definition found in this factory -> delegate to parent.
			return cbf.isFactoryBean(name);
		}
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * 返回指定的原型 bean 当前是否正在创建中（在当前线程内）。
	 * @param beanName bean 的名称
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set<?> set && set.contains(beanName))));
	}

	/**
	 * Callback before prototype creation.
	 * <p>The default implementation registers the prototype as currently in creation.
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String strValue) {
			Set<String> beanNameSet = CollectionUtils.newHashSet(2);
			beanNameSet.add(strValue);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set<?> beanNameSet) {
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * 返回去除 &前缀 的 循环别名映射到最后的规范化 bean 名称。
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * 返回规范化的 bean 名称。（是否前缀 & 跟随 入参）
	 *
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		// 获取规范化beanName
		String beanName = transformedBeanName(name);
		// 如果原始名称以&开头，重新添加前缀
		if (!name.isEmpty() && name.charAt(0) == BeanFactory.FACTORY_BEAN_PREFIX_CHAR) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * @param bw the BeanWrapper to initialize
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
		registerCustomEditors(bw);
	}

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		if (registry instanceof PropertyEditorRegistrySupport registrySupport) {
			registrySupport.useConfigValueEditors();
			if (!this.defaultEditorRegistrars.isEmpty()) {
				// Optimization: lazy overriding of default editors only when needed
				registrySupport.setDefaultEditorRegistrar(new BeanFactoryDefaultEditorRegistrar());
			}
		}
		else if (!this.defaultEditorRegistrars.isEmpty()) {
			// Fallback: proactive overriding of default editors
			applyEditorRegistrars(registry, this.defaultEditorRegistrars);
		}

		if (!this.propertyEditorRegistrars.isEmpty()) {
			applyEditorRegistrars(registry, this.propertyEditorRegistrars);
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}

	private void applyEditorRegistrars(PropertyEditorRegistry registry, Set<PropertyEditorRegistrar> registrars) {
		for (PropertyEditorRegistrar registrar : registrars) {
			try {
				registrar.registerCustomEditors(registry);
			}
			catch (BeanCreationException ex) {
				Throwable rootCause = ex.getMostSpecificCause();
				if (rootCause instanceof BeanCurrentlyInCreationException bce) {
					String bceBeanName = bce.getBeanName();
					if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
						if (logger.isDebugEnabled()) {
							logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
									"] failed because it tried to obtain currently created bean '" +
									ex.getBeanName() + "': " + ex.getMessage());
						}
						onSuppressedException(ex);
						return;
					}
				}
				throw ex;
			}
		}
	}

	/**
	 * 获取指定 bean 的合并后的 RootBeanDefinition。如果当前 bean 是一个子 bean 定义，
	 * 则会将其与父 bean 定义进行合并。
	 * @param beanName 要获取合并后定义的 bean 名称
	 * @return 合并后的 RootBeanDefinition，可能与原始定义不同（如果存在继承关系）
	 * @throws NoSuchBeanDefinitionException 如果找不到指定名称的 bean 定义
	 * @throws BeanDefinitionStoreException 如果 bean 定义存储出现问题（例如格式错误）
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// 检查缓存是否存在且可用
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			return mbd;
		}
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * 为给定的 bean 返回一个 RootBeanDefinition，如果给定 bean 的定义是一个子 bean 定义，
	 * 则通过与父 bean 定义合并来生成。
	 * @param beanName bean 定义的名称
	 * @param bd 原始 bean 定义（Root/ChildBeanDefinition）
	 * @param containingBd 外部bean，（内部bean，只能被单个bean使用，没有beanId，即 bean 创建时，new 出来的对象作为内部bean。外部bean则为持有这个内部bean的bean） 如果是顶级 bean 则为 {@code null}
	 * @return 给定 bean 的（可能合并后的）RootBeanDefinition
	 * @throws BeanDefinitionStoreException 如果 bean 定义无效
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;
			RootBeanDefinition previous = null;

			// 获取缓存的合并后的 RootBeanDefinition
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			if (mbd == null || mbd.stale) {
				previous = mbd;
				// 没有 父beanDefinition,当前bd为顶级bd，直接创建rootbd
				if (bd.getParentName() == null) {
					if (bd instanceof RootBeanDefinition rootBeanDef) {
						mbd = rootBeanDef.cloneBeanDefinition();
					}
					else {
						mbd = new RootBeanDefinition(bd);
					}
				}
				// 存在父bd，合并生成mbd
				else {
					BeanDefinition pbd;
					try {
						String parentBeanName = transformedBeanName(bd.getParentName());
						// 非同名bd
						if (!beanName.equals(parentBeanName)) {
							// 获取 父bd 的 mbd
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						// 同名bd
						else {
							// 同名的fbd，交给当前的父beanFactory 处理获取mbd
							if (getParentBeanFactory() instanceof ConfigurableBeanFactory parent) {
								pbd = parent.getMergedBeanDefinition(parentBeanName);
							}
							else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
												"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// 深拷贝并覆盖父级的属性值
					mbd = new RootBeanDefinition(pbd);
					mbd.overrideFrom(bd);
				}

				// 可能存在配置时，未说明作用域的情况，默认单例
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// 1、存在外部Bean定义 (containingBd != null)
				// 2、外部Bean是非单例的 (!containingBd.isSingleton())
				// 3、当前Bean是单例的 (mbd.isSingleton())
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					// 将当前 Bean 定义的作用域设置为与外部 Bean
					mbd.setScope(containingBd.getScope());
				}

				// 暂时缓存合并后的 bean 定义
				//（之后仍可能重新合并，以获取元数据的变更）
				if (containingBd == null && (isCacheBeanMetadata() || isBeanEligibleForMetadataCaching(beanName))) {
					// 优化性能，复用以下缓存的元数据：
					// 已解析的构造函数
					// 已解析的工厂方法
					// 目标类型信息
					// 其他与类结构相关但不会因配置变更而改变的元数据
					cacheMergedBeanDefinition(mbd, beanName);
				}
			}

			// 例如当 BeanDefinition 因为某些原因被标记为过期时：
			// 1. 父子关系发生变化
			// 2. 作用域发生变化
			// 3. 其他配置属性发生变化
			if (previous != null) {
				// 在一定情况下，使用之前的缓存 减少反射造成的资源消耗
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			return mbd;
		}
	}

	/**
	 * 将先前 mbd 的相关缓存复制到当前 Bean 定义中。
	 * <p>仅当两个 bd 具有相同的 beanClassName 、 factoryBeanName 和 FactoryMethodName 时，才会执行复制。
	 * 如果目标类型相同，或者当前 Bean 定义未指定目标类型，则会复制与类型相关的缓存。
	 * 此外，如果先前的 Bean 定义包含方法覆盖（Method Overrides），也会将其复制到当前 Bean 定义中。
	 *
	 * @param previous 先前的 RootBeanDefinition 实例
	 * @param mbd 当前的 RootBeanDefinition 实例
	 */
	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
			if (previous.hasMethodOverrides()) {
				mbd.setMethodOverrides(new MethodOverrides(previous.getMethodOverrides()));
			}
		}
	}

	/**
	 * Cache the given merged bean definition.
	 * <p>Subclasses can override this to derive additional cached state
	 * from the final post-processed bean definition.
	 * @param mbd the merged bean definition to cache
	 * @param beanName the name of the bean
	 * @since 6.2.6
	 */
	protected void cacheMergedBeanDefinition(RootBeanDefinition mbd, String beanName) {
		this.mergedBeanDefinitions.put(beanName, mbd);
	}

	/**
	 * 检查 mbd 是否为抽象类
	 * @param mbd 要检查的合并后的 bean 定义
	 * @param beanName bean 的名称
	 * @param args bean 创建时的参数（如果有）
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object @Nullable [] args) {
		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * for example, after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * 把 bean 定义中的类名（字符串）转换成真正的 Class 对象。
	 * 比如把 "com.example.UserService" 这个字符串变成 UserService.class 这个类对象，
	 * 然后把这个类对象保存起来，下次就不用重复转换了。
	 * @param mbd 合并后的 bean 定义，里面包含了类名信息
	 * @param beanName bean 的名字，出错时用来提示是哪个 bean
	 * @param typesToMatch ��要匹配的类型列表（主要用于内部类型检查，表示这个返回的 Class 不会直接给应用程序使用）
	 * @return 解析出来的 Class 对象，如果解析不了就返回 null
	 * @throws CannotLoadBeanClassException 如果找不到这个类或加载失败就抛异常
	 */
	protected @Nullable Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			if (mbd.hasBeanClass()) {
				return mbd.getBeanClass();
			}
			Class<?> beanClass = doResolveBeanClass(mbd, typesToMatch);
			if (mbd.hasBeanClass()) {
				mbd.prepareMethodOverrides();
			}
			return beanClass;
		}
		catch (ClassNotFoundException ex) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}
	}

	/**
	 * 解析Bean定义中的类名字符串为真正的Class对象
	 * 这是一个核心方法，负责将Bean定义中的类名（可能是表达式）转换为可用的Class对象
	 *
	 * @param mbd Bean的合并定义，包含类名等信息
	 * @param typesToMatch 需要匹配的类型数组，用于类型检查场景（非空时表示仅做类型检查，不实际创建实例）
	 * @return 解析出的Class对象，如果无法解析则返回null
	 * @throws ClassNotFoundException 当找不到指定的类时抛出
	 */
	// 使用typesToMatch的场景:
	// 类型检查：isTypeMatch() 方法调用时
	// 依赖注入验证：检查候选 Bean 是否匹配目标类型
	// Bean 查找：按类型查找 Bean 时的预检查
	// 在这些场景中，只需要知道 Bean 的类型信息，无需创建实际的实例，
	private @Nullable Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 获取Bean工厂的默认类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		ClassLoader dynamicLoader = beanClassLoader;
		// true: 表示后续需要重新解析类，不将解析结果缓存到 RootBeanDefinition 中
		boolean freshResolve = false;

		// 如果提供了类型匹配参数，说明这是用于类型检查而非实际创建Bean实例
		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// 当仅进行类型检查时（即还未创建实际实例），
			// 使用指定的临时类加载器（例如在织入场景中）
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				// 使用临时类加载器进行解析
				dynamicLoader = tempClassLoader;
				// 标记为重新解析，避免将结果缓存到Bean定义中
				// 因为通过临时类加载器解析的类可能与最终使用的类不同
				freshResolve = true;
				// 如果临时类加载器是装饰类加载器，排除需要匹配的类型
				// 这样可以避免在类型检查时产生不必要的类加载
				// DecoratingClassLoader允许排除特定的类，让它们由父类加载器处理
				if (tempClassLoader instanceof DecoratingClassLoader dcl) {
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		// 从Bean定义中获取类名
		String className = mbd.getBeanClassName();
		if (className != null) {
			// 评估Bean定义字符串，可能包含SpEL表达式
			// 例如："#{someBean.className}" 这样的表达式会被解析
			Object evaluated = evaluateBeanDefinitionString(className, mbd);

			// 如果评估后的结果与原始类名不同，说明是动态解析的表达式
			if (!className.equals(evaluated)) {
				// 支持动态解析的表达式，从4.2版本开始支持
				if (evaluated instanceof Class<?> clazz) {
					// 如果表达式直接返回Class对象，直接使用
					return clazz;
				}
				else if (evaluated instanceof String name) {
					// 如果表达式返回字符串，则不缓存，表达式结果可能发生变化
					className = name;
					freshResolve = true;
				}
				else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}

			// 如果需要重新解析（使用了临时类加载器或动态表达式）
			if (freshResolve) {
				// 当使用临时类加载器解析时，提前退出以避免将解析的Class存储在Bean定义中（因为这是临时的）
				if (dynamicLoader != null) {
					try {
						// 尝试使用动态类加载器加载类
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				// 使用Spring的ClassUtils工具类加载类
				// 这个方法会处理基本类型、数组类型等特殊情况
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// 常规解析，将结果缓存在Bean定义中...
		// 这是正常情况下的类解析，会将结果缓存起来避免重复解析
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 */
	protected @Nullable Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		// 如果没有配置表达式解析器，则直接返回原始值
		// 表达式解析器通常在ApplicationContext初始化时设置
		if (this.beanExpressionResolver == null) {
			return value;
		}

		// 构建表达式求值的上下文环境
			Scope scope = null;
		if (beanDefinition != null) {
			// 从Bean定义中获取作用域名称（如singleton、prototype、session等）
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				// 根据作用域名称获取对应的Scope实例
				// 这个Scope对象可以提供作用域相关的���量和方法给表达式使用
				scope = getRegisteredScope(scopeName);
			}
		}

		// 使用表达式解析器对字符串进行求值
		// BeanExpressionContext包含了求值所需的上下文信息：
		// - this: 当前的BeanFactory实例，表达式可以通过它访问其他Bean
		// - scope: 作用域对象，提供作用域相关的上下文
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type of the bean, or {@code null} if not predictable
	 */
	protected @Nullable Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();
		if (targetType != null) {
			return targetType;
		}
		if (mbd.getFactoryMethodName() != null) {
			return null;
		}
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. The implementation is allowed to instantiate the target factory bean if
	 * {@code allowInit} is {@code true} and the type cannot be determined another way;
	 * otherwise it is restricted to introspecting signatures and related metadata.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} is set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it.
	 * If subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails,
	 * a full FactoryBean creation as performed by this implementation should be used
	 * as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted if the type
	 * cannot be determined another way
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		try {
			ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
			if (result != ResolvableType.NONE) {
				return result;
			}
		}
		catch (IllegalArgumentException ex) {
			throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
					String.valueOf(ex.getMessage()));
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null ? ResolvableType.forClass(objectType) : ResolvableType.NONE);
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}

		// FactoryBean type not resolvable
		return ResolvableType.NONE;
	}

	/**
	 * 将指定的 bean 标记为已创建（或即将创建）。
	 * <p>这允许 bean 工厂优化其缓存以进行重复
	 * 创建指定的 bean。
	 * @param beanName bean 的名称
	 */
	protected void markBeanAsCreated(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {
				if (!isBeanEligibleForMetadataCaching(beanName)) {
					// 让 bean 定义在我们实际创建 bean 时重新合并，
					// 以防其元数据在此期间发生了更改。
					clearMergedBeanDefinition(beanName);
				}
				this.alreadyCreated.add(beanName);
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		synchronized (this.mergedBeanDefinitions) {
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * 判断指定的 bean 是否符合缓存 其定义元数据 的条件。
	 * 当一个 bean 已经被创建过，那么它的元数据可以被安全地缓存。
	 * @param beanName bean 的名称
	 * @return 如果 bean 的元数据可以被缓存则返回 {@code true}
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			removeSingleton(beanName);
			return true;
		}
		else {
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * 获取给定 bean 实例的对象，可以是 bean 实例本身，也可以是其创建的对象（如果是 FactoryBean）。
	 *
	 * @param beanInstance 共享 Bean 实例
	 * @param requiredType 期望的 bean 类型（如果有）
	 * @param name 命名可能包含工厂取消引用前缀的名称 factoryBean 包含&为前缀
	 * @param beanName 规范的 beanName
	 * @param mbd
	 * @return 要为 bean 公开的���象
	 */
	protected Object getObjectForBeanInstance(Object beanInstance, @Nullable Class<?> requiredType,
			String name, String beanName, @Nullable RootBeanDefinition mbd) {
		// 1. name 带 & 前缀（isFactoryDereference(name) 为 true），直接返回 FactoryBean 本身。
		// 2. name 不带 &，且 beanInstance 不是 FactoryBean，直接返回普通 Bean 实例。
		// 3. name 不带 &，但 beanInstance 是 FactoryBean，此时要调用 factoryBean.getObject()，返回该工厂创建的对象

		// name 为 & 开头，说明本次调用需要的是 factoryBean。
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			//首先判断是否是 NullBean 占位符，若是则直接返回它。
			if (beanInstance instanceof NullBean) {
				return beanInstance;
			}
			//检查实例是否实现了 FactoryBean 接口，若不是，则抛出 BeanIsNotAFactoryException。
			if (!(beanInstance instanceof FactoryBean)) {
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			//如果有合并后的 RootBeanDefinition（即 mbd 不为 null），则将其标记为工厂 Bean（mbd.isFactoryBean = true）。
			if (mbd != null) {
				mbd.isFactoryBean = true;
			}
			return beanInstance;
		}

		// sharedBeanInstance 不是 FactoryBean 实例，说明是bean，直接返回它。
		if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
			return beanInstance;
		}

		// 执行到这，说明该sharedBeanInstance一定是factoryBean
		Object object = null;
		if (mbd != null) {
			mbd.isFactoryBean = true;
		}
		else {
			object = getCachedObjectForFactoryBean(beanName);
		}
		// 如果缓存中没有找到对应的对象，则需要通过 FactoryBean 创建或获取对象。
		if (object == null) {
			// mbd 为 null，bd 存在，则获取mbd
			if (mbd == null && containsBeanDefinition(beanName)) {
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			// 合成 Bean 的典型场景：
			// AOP 代理：Spring AOP 创建的代理 Bean
			// 作用域代理：@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS) 生成的代理
			// 配置类代理：@Configuration 类的 CGLIB 代理
			// 内部基础设施 Bean：Spring 内部使用的辅助 Bean
			// 这个 synthetic 标志主要用于：
			// 错误处理：如果是合成 Bean，某些错误可能需要特殊处理
			// 日志记录：区分用户定义的 Bean 和框架生成的 Bean
			// 调试信息：帮助开发者理解 Bean 的来源
			// 后续处理逻辑：某些处理步骤可能对合成 Bean 有不同的行为

			//bean是否是合成的
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			object = getObjectFromFactoryBean(factoryBean, requiredType, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.
				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			}
			else {
				// A bean with a custom scope...
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object @Nullable [] args)
			throws BeanCreationException;


	/**
	 * CopyOnWriteArrayList which resets the beanPostProcessorCache field on modification.
	 *
	 * @since 5.3
	 */
	@SuppressWarnings("serial")
	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			resetBeanPostProcessorCache();
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			resetBeanPostProcessorCache();
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			resetBeanPostProcessorCache();
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				resetBeanPostProcessorCache();
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			resetBeanPostProcessorCache();
		}
	}


	/**
	 * Internal cache of pre-filtered post-processors.
	 *
	 * @since 5.3
	 */
	static class BeanPostProcessorCache {

		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}


	/**
	 * {@link PropertyEditorRegistrar} that delegates to the bean factory's
	 * default registrars, adding exception handling for circular reference
	 * scenarios where an editor tries to refer back to the currently created bean.
	 *
	 * @since 6.2.3
	 */
	class BeanFactoryDefaultEditorRegistrar implements PropertyEditorRegistrar {

		@Override
		public void registerCustomEditors(PropertyEditorRegistry registry) {
			applyEditorRegistrars(registry, defaultEditorRegistrars);
		}
	}

}
