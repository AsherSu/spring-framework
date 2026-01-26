package org.springframework.spring_reading.aop.EnableAspectJAutoProxy;


import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MyAspect {

	@Before("execution(* org.springframework.spring_reading.aop.EnableAspectJAutoProxy.MyService+.*(..))")
	public void before() {
		System.out.println("Before method execution");
	}
}
