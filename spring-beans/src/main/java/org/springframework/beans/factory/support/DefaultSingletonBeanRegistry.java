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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/** Common lock for singleton creation. */
	final Lock singletonLock = new ReentrantLock();

	/** 单例对象的缓存：Bean 名称到 Bean 实例。 一级缓存*/
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** 三级缓存：beanName 映射到 Bean工厂（创建早期bean的逻辑）*/
	private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(16);

	/** Custom callbacks for singleton creation/registration. */
	private final Map<String, Consumer<Object>> singletonCallbacks = new ConcurrentHashMap<>(16);

	/** 早期单例对象的缓存：Bean 名称到 Bean 实例  二级缓存*/
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/** Set of registered singletons, containing the bean names in registration order. */
	private final Set<String> registeredSingletons = Collections.synchronizedSet(new LinkedHashSet<>(256));

	/** 正在创建中的 Bean 名称集合。 */
	private final Set<String> singletonsCurrentlyInCreation = ConcurrentHashMap.newKeySet(16);

	/** 排除某些 Bean 进行循环依赖检查 */
	private final Set<String> inCreationCheckExclusions = ConcurrentHashMap.newKeySet(16);

	/** 用于 宽松创建跟踪（lct） 的特定锁。 */
	private final Lock lenientCreationLock = new ReentrantLock();

	/** lct是否创建完成的标志锁 */
	private final Condition lenientCreationFinished = this.lenientCreationLock.newCondition();

	/** 当前处于宽松创建模式的 Bean 名称集合。 */
	private final Set<String> singletonsInLenientCreation = new HashSet<>();

	/** 映射：等待线程 映射 创建bean的线程*/
	private final Map<Thread, Thread> lenientWaitingThreads = new HashMap<>();

	/** 当前正在创建的 bean 实例与 其创建线程 的映射关系。 */
	private final Map<String, Thread> currentCreationThreads = new ConcurrentHashMap<>();

	/** 标志当前spring环境是否正在执行 destroySingletons 操作，即销毁环境。 */
	private volatile boolean singletonsCurrentlyInDestruction = false;

	/** Collection of suppressed Exceptions, available for associating related causes. */
	private @Nullable Set<Exception> suppressedExceptions;

	/** Disposable bean instances: bean name to disposable instance. */
	private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Bean依赖关系映射：被依赖的Bean名称 -> 依赖它的Bean名称集合（depBeanName->beanName） */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** 在依赖 bean 名称之间映射：beanName -> 依赖的 beanName 集。（beanName->depBeanName）*/
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		this.singletonLock.lock();
		try {
			addSingleton(beanName, singletonObject);
		}
		finally {
			this.singletonLock.unlock();
		}
	}

	/**
	 * Add the given singleton object to the singleton registry.
	 * <p>To be called for exposure of freshly registered/created singletons.
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		Object oldObject = this.singletonObjects.putIfAbsent(beanName, singletonObject);
		if (oldObject != null) {
			throw new IllegalStateException("Could not register object [" + singletonObject +
					"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
		}
		this.singletonFactories.remove(beanName);
		this.earlySingletonObjects.remove(beanName);
		this.registeredSingletons.add(beanName);

		Consumer<Object> callback = this.singletonCallbacks.get(beanName);
		if (callback != null) {
			callback.accept(singletonObject);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for early exposure purposes, for example, to be able to
	 * resolve circular references.
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		this.singletonFactories.put(beanName, singletonFactory);
		this.earlySingletonObjects.remove(beanName);
		this.registeredSingletons.add(beanName);
	}

	@Override
	public void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer) {
		this.singletonCallbacks.put(beanName, singletonConsumer);
	}

	@Override
	public @Nullable Object getSingleton(String beanName) {
		// 调用重载的getSingleton方法来获取单例bean。
		// 参数说明：
		// 1. beanName: 要获取的单例bean的名称。
		// 2. true: 表示如果当前bean正在创建中（例如处理循环引用的情况），则允许返回早期的单例bean引用。
		return getSingleton(beanName, true);
	}

	/**
	 * 返回在给定名称下注册的（原始）单例对象。
	 * 检查已经实例化的单例，还允许早期引用当前正在创建的单例（解决循环引用问题）。
	 * @param beanName 命名要查找的 bean 的名称
	 * @param allowEarlyReference 是否应该创建早期引用
	 * @return 已注册的单例对象 或者 早期单例bean，如果未找到，则为 {@code null}
	 */
	protected @Nullable Object getSingleton(String beanName, boolean allowEarlyReference) {
		// 尝试从缓存中快速检索已存在的bean实例，避免完全锁定单例
		Object singletonObject = this.singletonObjects.get(beanName);

		// 如果找不到实例，并且该bean当前正在创建中（例如，处理循环引用）
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			singletonObject = this.earlySingletonObjects.get(beanName);

			// 如果允许提前引用并且在早期单例对象中仍未找到
			if (singletonObject == null && allowEarlyReference) {
				// 加锁创建早期引用（earlySingletonObjects 被实例化但是未完成注入，前后置的方法等）时，避免在单例锁中阻塞其他线程，
				synchronized (this.singletonObjects) {
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							// 从三级缓存中获取单例工厂，创建早期bean
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								singletonObject = singletonFactory.getObject();
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * 返回在给定名称下注册的（原始）单例对象，如果尚未注册，则创建并注册一个新实例。
	 * @param beanName bean的名称
	 * @param singletonFactory 用于延迟创建单例的ObjectFactory（如有必要）
	 * @return 已注册的单例对象
	 */
	@SuppressWarnings("NullAway") // Dataflow analysis limitation
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		// 确保 beanName 不为 null
		Assert.notNull(beanName, "Bean name must not be null");

		// 获取当前线程
		Thread currentThread = Thread.currentThread();
		// 判断当前线程是否允许持有单例锁
		Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
		// 确定是否需要尝试获取锁
		boolean acquireLock = !Boolean.FALSE.equals(lockFlag);
		// 尝试获取单例锁
		boolean locked = (acquireLock && this.singletonLock.tryLock());

		try {
			// 先从单例缓存中尝试获取已存在的实例
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				// 如果需要获取锁但没有成功获取
				if (acquireLock && !locked) {
					// 如果明确允许当前线程持有锁（但当前锁被其他线程占用）
					if (Boolean.TRUE.equals(lockFlag)) {
						// 另一个线程正在单例工厂回调中忙碌，可能被阻塞。
						// 从 6.2 版本开始的回退机制：在单例锁之外处理给定的单例 bean。
						// 线程安全的暴露仍然有保证，但在触发当前 bean 的依赖项创建时可能会有冲突风险。
						this.lenientCreationLock.lock();
						try {
							if (logger.isInfoEnabled()) {
								Set<String> lockedBeans = new HashSet<>(this.singletonsCurrentlyInCreation);
								lockedBeans.removeAll(this.singletonsInLenientCreation);
								logger.info("Obtaining singleton bean '" + beanName + "' in thread \"" +
										currentThread.getName() + "\" while other thread holds singleton " +
										"lock for other beans " + lockedBeans);
							}
							// 将当前 bean 标记为宽松创建模式
							this.singletonsInLenientCreation.add(beanName);
						}
						finally {
							// 释放宽松创建锁
							this.lenientCreationLock.unlock();
						}
					}
					else {
						// 没有特定的锁定指示（例如宽松lct）并且单例锁当前被其他创建方法持有 -> 等待。
						this.singletonLock.lock();
						locked = true;
						// 在等待期间，单例对象可能已经被创建。
						singletonObject = this.singletonObjects.get(beanName);
						if (singletonObject != null) {
							return singletonObject;
						}
					}
				}

				// 环境正在销毁过程中
				if (this.singletonsCurrentlyInDestruction) {
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}

				// 在创建单例之前执行前置检查
				try {
					// 放入正在创建的单例池 singletonsCurrentlyInCreation 里
					beforeSingletonCreation(beanName);
				}
				catch (BeanCurrentlyInCreationException ex) {
					// 如果捕获到 BeanCurrentlyInCreationException，说明该 bean 正在创建中，即第二次创建
					this.lenientCreationLock.lock();
					try {
						// 循环等待直到单例对象被创建或满足退出条件
						while ((singletonObject = this.singletonObjects.get(beanName)) == null) {
							// 获取当前正在创建该 bean 的线程
							Thread otherThread = this.currentCreationThreads.get(beanName);
							// 如果是当前线程或存在依赖等待关系，则抛出异常
							if (otherThread != null && (otherThread == currentThread ||
									checkDependentWaitingThreads(otherThread, currentThread))) {
								throw ex;
							}
							// 如果该 bean 不在宽松创建列表中，则退出循环
							if (!this.singletonsInLenientCreation.contains(beanName)) {
								break;
							}
							// 如果存在其他线程，则记录等待关系
							if (otherThread != null) {
								this.lenientWaitingThreads.put(currentThread, otherThread);
							}
							try {
								// 等待创建完成信号
								this.lenientCreationFinished.await();
							}
							catch (InterruptedException ie) {
								// 如果被中断，则恢复中断状态
								currentThread.interrupt();
							}
							finally {
								// 清理等待线程记录
								if (otherThread != null) {
									this.lenientWaitingThreads.remove(currentThread);
								}
							}
						}
					}
					finally {
						// 释放宽松创建锁
						this.lenientCreationLock.unlock();
					}
					// 如果已经获取到单例对象，则直接返回
					if (singletonObject != null) {
						return singletonObject;
					}
					// 如果已经持有锁，则抛出异常
					if (locked) {
						throw ex;
					}
					// 尝试延迟获取锁以等待特定 bean 创建完成
					this.singletonLock.lock();
					locked = true;

					// 锁创建的单例对象可能已经在这期间出现。
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject != null) {
						return singletonObject;
					}
					// 再次执行前置检查
					beforeSingletonCreation(beanName);
				}

				// 标记是否是新创建的单例
				boolean newSingleton = false;
				// 初始化被抑制的异常
				boolean recordSuppressedExceptions = (locked && this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}

				try {
					singletonObject = this.singletonObjects.get(beanName);
					// 如果仍未获取到单例对象，则通过工厂创建
					if (singletonObject == null) {
						// 记录当前正在创建该 bean 的线程
						this.currentCreationThreads.put(beanName, currentThread);
						try {
							// 通过 ObjectFactory 创建单例对象
							singletonObject = singletonFactory.getObject();
						}
						finally {
							// 移除当前线程记录
							this.currentCreationThreads.remove(beanName);
						}
						// 标记为新创建的单例
						newSingleton = true;
					}
				}
				catch (IllegalStateException ex) {
					// 检查单例对象是否在这期间隐式出现 ->
					// 如果是，则继续处理，因为异常表明了这种状态。
					singletonObject = this.singletonObjects.get(beanName);
					// 如果仍未获取到单例对象，则抛出异常
					if (singletonObject == null) {
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					// 如果需要记录被抑制的异常，则将它们添加到主异常中
					if (recordSuppressedExceptions) {
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					// 抛出 BeanCreationException
					throw ex;
				}
				finally {
					// 如果记录了被抑制的异常，则清空集合
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					// 执行创建后的清理工作
					afterSingletonCreation(beanName);
				}

				// 如果是新创建的单例，则将其添加到单例注册表中
				if (newSingleton) {
					try {
						addSingleton(beanName, singletonObject);
					}
					catch (IllegalStateException ex) {
						// 如果单例对象在这期间隐式出现，则接受相同实例。
						Object object = this.singletonObjects.get(beanName);
						// 如果实例不一致，则抛出异常
						if (singletonObject != object) {
							throw ex;
						}
					}
				}
			}
			// 返回单例对象
			return singletonObject;
		}
		finally {
			// 如果持有锁，则释放单例锁
			if (locked) {
				this.singletonLock.unlock();
			}
			// 获取宽松创建锁
			this.lenientCreationLock.lock();
			try {
				// 从宽松创建列表中移除当前 bean
				this.singletonsInLenientCreation.remove(beanName);
				// 清理等待线程记录
				this.lenientWaitingThreads.entrySet().removeIf(
						entry -> entry.getValue() == currentThread);
				// 通知所有等待线程
				this.lenientCreationFinished.signalAll();
			}
			finally {
				// 释放宽松创建锁
				this.lenientCreationLock.unlock();
			}
		}
	}

	private boolean checkDependentWaitingThreads(Thread waitingThread, Thread candidateThread) {
		Thread threadToCheck = waitingThread;
		while ((threadToCheck = this.lenientWaitingThreads.get(threadToCheck)) != null) {
			if (threadToCheck == candidateThread) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether the current thread is allowed to hold the singleton lock.
	 * <p>By default, all threads are forced to hold a full lock through {@code null}.
	 * {@link DefaultListableBeanFactory} overrides this to specifically handle its
	 * threads during the pre-instantiation phase: {@code true} for the main thread,
	 * {@code false} for managed background threads, and configuration-dependent
	 * behavior for unmanaged threads.
	 * @return {@code true} if the current thread is explicitly allowed to hold the
	 * lock but also accepts lenient fallback behavior, {@code false} if it is
	 * explicitly not allowed to hold the lock and therefore forced to use lenient
	 * fallback behavior, or {@code null} if there is no specific indication
	 * (traditional behavior: forced to always hold a full lock)
	 * @since 6.2
	 */
	protected @Nullable Boolean isCurrentThreadAllowedToHoldSingletonLock() {
		return null;
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, for example, a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
			this.suppressedExceptions.add(ex);
		}
	}

	/**
	 * Remove the bean with the given name from the singleton registry, either on
	 * regular destruction or on cleanup after early exposure when creation failed.
	 * @param beanName the name of the bean
	 */
	protected void removeSingleton(String beanName) {
		this.singletonObjects.remove(beanName);
		this.singletonFactories.remove(beanName);
		this.earlySingletonObjects.remove(beanName);
		this.registeredSingletons.remove(beanName);
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		return StringUtils.toStringArray(this.registeredSingletons);
	}

	@Override
	public int getSingletonCount() {
		return this.registeredSingletons.size();
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 返回指定的单例 Bean 当前是否正在创建中（整个工厂内）。
	 * @param beanName 要检查的 Bean 名称
	 */
	public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 在单例bean创建之前回调此方法。
	 * <p>默认实现将该单例bean标记为正在创建中。
	 * @param beanName 即将被创建的单例bean的名称
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 单例创建完成后的回调方法
	 * <p>T默认实现将该单例bean标记为不再处于创建状态
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * for example, between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 为给定的 bean 注册一个依赖 bean，以便在给定 bean 被销毁之前先销毁依赖 bean。
	 * @param beanName 给定 bean 的名称
	 * @param dependentBeanName 依赖 bean 的名称
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 检查指定的依赖 bean 是否直接或间接依赖于给定的 bean。
	 * @param beanName 要检查的 bean 的名称
	 * @param dependentBeanName 依赖 bean 的名称
	 * 依赖关系：dependentBeanName -> beanName
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		// 已访问过的bean集合，用于防止循环依赖导致的无限递归
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		// 通过规范化名称从依赖映射表中获取当前bean的所有依赖项
		String canonicalName = canonicalName(beanName);
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null || dependentBeans.isEmpty()) {
			return false;
		}
		// 如果依赖项集合中包含目标依赖bean，则直接返回true
		if (dependentBeans.contains(dependentBeanName)) {
			return true;
		}
		if (alreadySeen == null) {
			alreadySeen = new HashSet<>();
		}
		alreadySeen.add(beanName);
		// 遍历所有直接依赖的bean，递归检查它们是否依赖于目标依赖bean
		for (String transitiveDependency : dependentBeans) {
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		this.singletonsCurrentlyInDestruction = true;

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		this.singletonLock.lock();
		try {
			clearSingletonCache();
		}
		finally {
			this.singletonLock.unlock();
		}
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		this.singletonObjects.clear();
		this.singletonFactories.clear();
		this.earlySingletonObjects.clear();
		this.registeredSingletons.clear();
		this.singletonsCurrentlyInDestruction = false;
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Destroy the corresponding DisposableBean instance.
		// This also triggers the destruction of dependent beans.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);

		// destroySingletons() removes all singleton instances at the end,
		// leniently tolerating late retrieval during the shutdown phase.
		if (!this.singletonsCurrentlyInDestruction) {
			// For an individual destruction, remove the registered instance now.
			// As of 6.2, this happens after the current bean's destruction step,
			// allowing for late bean retrieval by on-demand suppliers etc.
			if (this.currentCreationThreads.get(beanName) == Thread.currentThread()) {
				// Local remove after failed creation step -> without singleton lock
				// since bean creation may have happened leniently without any lock.
				removeSingleton(beanName);
			}
			else {
				this.singletonLock.lock();
				try {
					removeSingleton(beanName);
				}
				finally {
					this.singletonLock.unlock();
				}
			}
		}
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependentBeanNames;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependentBeanNames = this.dependentBeanMap.remove(beanName);
		}
		if (dependentBeanNames != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
			}
			for (String dependentBeanName : dependentBeanNames) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	@Deprecated(since = "6.2")
	@Override
	public final Object getSingletonMutex() {
		return new Object();
	}

}
