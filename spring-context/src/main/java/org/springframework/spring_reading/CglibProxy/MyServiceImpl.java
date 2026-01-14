package org.springframework.spring_reading.CglibProxy;

public class MyServiceImpl implements MyService {

	@Override
	public void doSomething() {
		System.out.println("hello world");
	}
}