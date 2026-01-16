package org.springframework.spring_reading.aop.JdkProxy;

public class MyServiceImpl implements MyService {

	@Override
	public void doSomething() {
		System.out.println("hello world");
	}
}