package org.springframework.spring_reading.Bean.Processor.BeanPostProcessor;

public class MyServiceImpl implements MyService{

	private String message = "Hello from MyService";

	@Override
	public String show() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getMessage() {
		return message;
	}
}
