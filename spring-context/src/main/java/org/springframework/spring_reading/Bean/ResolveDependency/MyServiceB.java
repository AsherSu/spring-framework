package org.springframework.spring_reading.Bean.ResolveDependency;

import org.springframework.beans.factory.annotation.Value;

public class MyServiceB {

	/**
	 * 方法注入
	 */
	private MyServiceA methodMyServiceA;

	/**
	 * 字段注入
	 */
	private MyServiceA fieldMyServiceA;

	/**
	 * 字段注入 (环境变量)
	 */
	@Value("${my.property.value}")
	private String myPropertyValue;

	public void setMethodMyServiceA(MyServiceA methodMyServiceA){
		this.methodMyServiceA = methodMyServiceA;
	}

	@Override
	public String toString() {
		return "MyServiceB{" +
				"myPropertyValue='" + myPropertyValue + '\'' +
				", methodMyServiceA=" + methodMyServiceA +
				", fieldMyServiceA=" + fieldMyServiceA +
				'}';
	}
}
