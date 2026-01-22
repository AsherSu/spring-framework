package org.springframework.spring_reading.Bean.Processor.InstantiationAwareBeanPostProcessor;

import org.springframework.beans.factory.annotation.Value;

public class MyDataBaseImpl implements MyDataBase {

	@Value("root")
	private String username;

	@Value("123456")
	private String password;

	private boolean postInstantiationFlag;

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public void setPassword(String password) {
		this.password = password;
	}

	@Override
	public boolean isPostInstantiationFlag() {
		return postInstantiationFlag;
	}

	@Override
	public void setPostInstantiationFlag(boolean postInstantiationFlag) {
		this.postInstantiationFlag = postInstantiationFlag;
	}
}
