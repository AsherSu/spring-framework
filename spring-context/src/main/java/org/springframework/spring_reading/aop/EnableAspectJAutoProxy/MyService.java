package org.springframework.spring_reading.aop.EnableAspectJAutoProxy;


import org.springframework.stereotype.Service;

@Service
public class MyService {

	public void foo() {
		System.out.println("foo...");
	}
}
