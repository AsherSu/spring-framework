package org.springframework.spring_reading.aop.CglibProxy;

import org.springframework.cglib.proxy.Enhancer;

// 参考： https://www.cnblogs.com/codegitz/p/15843084.html
// 利用java的动态绑定：不管你表面上叫什么，JVM 运行时只看你肚子里（内存里）到底是谁。
// 静态方法，成员变量不具备 多态性 ，看引用类型
public class CglibProxyDemo {

	public static void main(String[] args) {
		// 创建 Enhancer 对象，用于生成代理类
		Enhancer enhancer = new Enhancer();
		// 设置目标对象的父类
		enhancer.setSuperclass(MyServiceImpl.class);
		// 设置回调拦截器
		enhancer.setCallback(new MyMethodInterceptor());
		// 创建代理对象
		MyServiceImpl proxyObject = (MyServiceImpl) enhancer.create();
		// 输出代理对象的类名
		System.out.println("ProxyObject = " + proxyObject.getClass());
		// 调用代理对象的方法
		proxyObject.doSomething();
	}
}

/**
 * package com.example.demo;
 *
 * import net.sf.cglib.proxy.*;
 * import java.lang.reflect.Method;
 *
 * // 1. 【继承】 自动继承了你的目标类 UserDao
 * // 2. 【实现Factory】 实现了 Factory 接口（用于管理回调）
 * public class UserDao$$EnhancerByCGLIB$$3a349270 extends UserDao implements Factory {
 *
 *     // ================= 核心变量区 =================
 *
 *     // 这就是我们在 Enhancer.setCallback 传进去的拦截器！
 *     private MethodInterceptor CGLIB$CALLBACK_0;
 *
 *     // 缓存下来的 Java 反射 Method 对象（也就是 UserDao.save()）
 *     private static final Method CGLIB$save$0$Method;
 *
 *     // 核心：MethodProxy 对象（包含了 FastClass 索引，用来实现高性能调用）
 *     private static final MethodProxy CGLIB$save$0$Proxy;
 *
 *     // ================= 静态代码块（初始化元数据） =================
 *     static {
 *         try {
 *             // 初始化 save 方法的反射对象
 *             CGLIB$save$0$Method = Class.forName("com.example.UserDao").getMethod("save", new Class[0]);
 *
 *             // 初始化 MethodProxy
 *             // 参数说明：(目标类, 代理类, 方法签名, 代理方法签名, 索引号)
 *             CGLIB$save$0$Proxy = MethodProxy.create(
 *                 UserDao.class,
 *                 UserDao$$EnhancerByCGLIB$$3a349270.class,
 *                 "()V",
 *                 "save",
 *                 "CGLIB$save$0"
 *             );
 *         } catch (Exception e) {
 *             e.printStackTrace();
 *         }
 *     }
 *
 *     // ================= 构造函数 =================
 *     public UserDao$$EnhancerByCGLIB$$3a349270() {
 *         super();
 *     }
 *
 *     // 设置拦截器的方法（Factory接口要求的）
 *     public void setCallback(int index, Callback callback) {
 *         if (index == 0) {
 *             this.CGLIB$CALLBACK_0 = (MethodInterceptor) callback;
 *         }
 *     }
 *
 *     // ================= 核心：被重写的方法 (Trap) =================
 *
 *     // 重写了父类的 save 方法
 *     @Override
 *     public final void save() {
 *         MethodInterceptor interceptor = this.CGLIB$CALLBACK_0;
 *
 *         // 如果有拦截器，就走拦截器逻辑
 *         if (interceptor != null) {
 *             try {
 *                 // ！！！关键点！！！
 *                 // 调用拦截器的 intercept 方法
 *                 // 传参：(this代理对象, save方法对象, 空参数, MethodProxy对象)
 *                 interceptor.intercept(this, CGLIB$save$0$Method, new Object[0], CGLIB$save$0$Proxy);
 *             } catch (Throwable e) {
 *                 throw new RuntimeException(e);
 *             }
 *         } else {
 *             // 如果没有设置拦截器，就直接调用父类方法
 *             super.save();
 *         }
 *     }
 *
 *     // ================= 核心：专门给 FastClass 调用的方法 (Escape Route) =================
 *
 *     // 这个方法是专门留给 MethodProxy.invokeSuper() 调用的
 *     // 它的作用极其简单：就是回过头去调用父类（目标对象）的原生逻辑
 *     // 避免了死循环
 *     final void CGLIB$save$0() {
 *         super.save();
 *     }
 * }
 */


