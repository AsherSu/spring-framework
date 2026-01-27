package org.springframework.spring_reading.aop.JdkProxy;

import java.lang.reflect.Proxy;

public class JdkProxyDemo {

	public static void main(String[] args) {
		// 创建目标对象
		MyService target = new MyServiceImpl();
		// 获取目标对象的类对象
		Class clz = target.getClass();
		// 创建代理对象，并指定目标对象的类加载器、实现的接口以及调用处理器
		MyService proxyObject = (MyService) Proxy.newProxyInstance(clz.getClassLoader(), clz.getInterfaces(), new MyInvocationHandler(target));
		// 打印代理对象的类信息
		System.out.println("ProxyObject = " + proxyObject.getClass());
		// 通过代理对象调用方法，实际上会调用 MyInvocationHandler 中的 invoke 方法
		proxyObject.doSomething();
	}
}

// $Proxy0 调用invoke-> InvocationHandler.invoke() -> MyInvocationHandler.invoke() -> MyInterface.play() 使用被代理对象执行目标方法，增强作用
// $Proxy0 实现接口-> MyInterface.play() 模仿被代理对象执行目标方法
// $Proxy0 集成Proxy-> Proxy 标识身份，模板类

/**
 * public final class $Proxy0 extends Proxy implements MyInterface {
 *     private static Method m1;
 *     private static Method m0;
 *     private static Method m3;
 *     private static Method m2;
 *
 *     static {
 *         try {
 *             $Proxy0.m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
 *             $Proxy0.m0 = Class.forName("java.lang.Object").getMethod("hashCode", (Class<?>[])new Class[0]);
 *             // 实例化 MyInterface 的 play()方法
 *             $Proxy0.m3 = Class.forName("com.shuitu.test.MyInterface").getMethod("play", (Class<?>[])new Class[0]);
 *             $Proxy0.m2 = Class.forName("java.lang.Object").getMethod("toString", (Class<?>[])new Class[0]);
 *         }
 *         catch (NoSuchMethodException ex) {
 *             throw new NoSuchMethodError(ex.getMessage());
 *         }
 *         catch (ClassNotFoundException ex2) {
 *             throw new NoClassDefFoundError(ex2.getMessage());
 *         }
 *     }
 *
 *     public $Proxy0(final InvocationHandler invocationHandler) {
 *         super(invocationHandler);
 *     }
 *
 *     public final void play() {
 *         try {
 *         	// 这个 h 其实就是我们调用 Proxy.newProxyInstance()方法 时传进去的 ProxyFactory对象(它实现了
 *          // InvocationHandler接口)，该对象的 invoke()方法 中实现了对目标对象的目标方法的增强。
 *         	// 看到这里，利用动态代理实现方法增强的实现原理就全部理清咯
 *             super.h.invoke(this, $Proxy0.m3, null);
 *         }
 *         catch (Error | RuntimeException error) {
 *             throw new RuntimeException();
 *         }
 *         catch (Throwable t) {
 *             throw new UndeclaredThrowableException(t);
 *         }
 *     }
 *
 *     public final boolean equals(final Object o) {
 *         try {
 *             return (boolean)super.h.invoke(this, $Proxy0.m1, new Object[] { o });
 *         }
 *         catch (Error | RuntimeException error) {
 *             throw new RuntimeException();
 *         }
 *         catch (Throwable t) {
 *             throw new UndeclaredThrowableException(t);
 *         }
 *     }
 *
 *     public final int hashCode() {
 *         try {
 *             return (int)super.h.invoke(this, $Proxy0.m0, null);
 *         }
 *         catch (Error | RuntimeException error) {
 *             throw new RuntimeException();
 *         }
 *         catch (Throwable t) {
 *             throw new UndeclaredThrowableException(t);
 *         }
 *     }
 *
 *     public final String toString() {
 *         try {
 *             return (String)super.h.invoke(this, $Proxy0.m2, null);
 *         }
 *         catch (Error | RuntimeException error) {
 *             throw new RuntimeException();
 *         }
 *         catch (Throwable t) {
 *             throw new UndeclaredThrowableException(t);
 *         }
 *     }
 * }
 */