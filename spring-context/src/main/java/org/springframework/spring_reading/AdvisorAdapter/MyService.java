package org.springframework.spring_reading.AdvisorAdapter;

public class MyService {

	public String foo() {
		System.out.println("foo...");
		return "this is a foo";
	}

	public String bar() {
		System.out.println("bar...");
		return null;
	}
}
