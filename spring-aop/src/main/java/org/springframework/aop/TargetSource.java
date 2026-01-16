/*
 * Copyright 2002-present the original author or authors.
 * ...
 */

package org.springframework.aop;

import org.jspecify.annotations.Nullable;

/**
 * [通俗定义]：这是“目标对象供应商”接口。
 * * 想象一下：代理对象（Proxy）只是一个前台接待。当有业务请求（比如“做一份炒饭”）进来时，
 * 代理对象自己不会做饭，它必须找一个真正的厨师（Target）来做。
 * * 这里的 TargetSource 就是那个负责“分配厨师”的经理。
 * 代理对象持有这个经理（TargetSource），每次要干活时，就问经理要人。
 *
 * <p>如果这个经理很死板（Static），他永远指派同一个厨师（Singleton Bean），那么效率很高，不用反复交接。
 * <p>如果这个经理很灵活（Dynamic），他可能从休息室（对象池 Pooling）拉一个人出来，或者临时雇佣一个临时工（Prototype），
 * 甚至支持热插拔（Hot Swapping，干活干一半换人）。
 *
 * <p>普通开发者通常不需要直接写这个，这是给框架底层用的。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public interface TargetSource extends TargetClassAware {

	/**
	 * [查户口]：你供应的厨师是哪个流派的？
	 * * 返回目标对象的类型（比如 UserServiceImpl.class）。
	 * 既然继承了 TargetClassAware，它必须得能回答这个问题。
	 * * @return 目标类型
	 */
	@Override
	@Nullable Class<?> getTargetClass();

	/**
	 * [关键询问]：你每次给我的都是同一个人吗？
	 * * <p>如果是 true（静态）：
	 * 表示目标对象是单例的（Singleton）。Spring 会进行优化，拿一次之后就缓存起来，
	 * 以后不用每次都调 getTarget()，也不用调 releaseTarget()。
	 * * <p>如果是 false（动态）：
	 * 表示每次可能给我不同的人（比如多例 Prototype，或者从 CommonsPool 里借一个）。
	 * 那样每次调用完，我必须记得调 releaseTarget() 把人还回去。
	 * * <p>默认实现是 false（但在 Spring 中最常用的 SingletonTargetSource 会重写为 true）。
	 * @return 如果目标对象不可变/单例，返回 true
	 */
	default boolean isStatic() {
		return false;
	}

	/**
	 * [我要人了]：快给我一个干活的对象！
	 * * 这个方法会在 AOP 拦截链即将执行到底层业务逻辑 *之前* 被调用。
	 * * @return 真正干活的那个对象（Target Object）。
	 * @throws Exception 如果供应商找不到人（比如数据库连接池空了），可能会抛异常。
	 */
	@Nullable Object getTarget() throws Exception;

	/**
	 * [完事归还]：活干完了，这个对象还给你。
	 * * <p>只有当 isStatic() 为 false 时，代理对象在方法执行完后，才会调用这个方法。
	 * * 用途举例：
	 * 1. 也就是多例（Prototype）：这里可能什么都不做，留给 GC 回收。
	 * 2. 对象池（Pooling）：这里必须把对象放回池子里（returnObject），否则池子就漏水了。
	 * 3. 数据库连接：关闭连接或归还连接。
	 * * @param target 刚才通过 getTarget() 拿到的那个对象
	 */
	default void releaseTarget(Object target) throws Exception {
	}

}