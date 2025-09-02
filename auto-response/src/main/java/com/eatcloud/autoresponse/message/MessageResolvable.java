package com.eatcloud.autoresponse.message;

public interface MessageResolvable {
	String message();
	default String code() {
		return null;
	}
}

