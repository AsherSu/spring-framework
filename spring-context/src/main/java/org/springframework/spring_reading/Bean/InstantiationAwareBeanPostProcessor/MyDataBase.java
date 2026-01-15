package org.springframework.spring_reading.Bean.InstantiationAwareBeanPostProcessor;

public interface MyDataBase {
	String getUsername();
	void setUsername(String username);
	String getPassword();
	void setPassword(String password);
	boolean isPostInstantiationFlag();
	void setPostInstantiationFlag(boolean flag);
}
