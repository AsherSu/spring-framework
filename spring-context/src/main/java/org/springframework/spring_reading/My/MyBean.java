package org.springframework.spring_reading.My;

import java.io.Serializable;

@MyClassAnnotation
public final class MyBean extends MyAbstract implements Serializable {

	private static final long serialVersionUID = 1L;

	public String key;

	public String value;

	@MyAnnotation
	public static void myMethod1() {

	}

	@MyAnnotation
	public String myMethod2() {
		return "hello world";
	}

	public void myMethod3() {

	}

	public static class MyInnerClass {
		// 内部类的定义
	}
}
