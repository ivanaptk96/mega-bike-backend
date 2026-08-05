package com.megabike.sef.infrastructure;

import org.springframework.http.HttpStatusCode;

public class SefApiException extends RuntimeException {

	private final HttpStatusCode statusCode;
	private final String responseBody;

	public SefApiException(String message, HttpStatusCode statusCode, String responseBody) {
		super(message);
		this.statusCode = statusCode;
		this.responseBody = responseBody;
	}

	public HttpStatusCode getStatusCode() {
		return statusCode;
	}

	public String getResponseBody() {
		return responseBody;
	}
}
