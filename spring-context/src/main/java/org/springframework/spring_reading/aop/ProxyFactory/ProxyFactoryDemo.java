package org.springframework.spring_reading.aop.ProxyFactory;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.spring_reading.AdvisorChainFactory.MyMethodBeforeAdvice;

// 手动创建代理对象
// 自定义指定 targetSource对象的interfaces 设置 由advisorChainFactory解析advisors出来的MethodInterceptor链条

public class ProxyFactoryDemo {

	public static void main(String[] args) {
		// 创建代理工厂&创建目标对象
		ProxyFactory proxyFactory = new ProxyFactory(new MyService());
		// 创建通知
		proxyFactory.addAdvice(new MyMethodBeforeAdvice());
		// 获取代理对象
		Object proxy = proxyFactory.getProxy();
		// 调用代理对象的方法
		System.out.println("proxy = " + proxy.getClass());
	}
}
