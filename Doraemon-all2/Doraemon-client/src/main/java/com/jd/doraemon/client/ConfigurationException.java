/**
 * 
 */
package com.jd.doraemon.client;

/**
 * @author luolishu
 * 
 */
public class ConfigurationException extends RuntimeException {
	private static final long serialVersionUID = 626988172028314858L;

	public ConfigurationException() {
		super();
	}

	public ConfigurationException(String message, Throwable cause) {
		super(message, cause);
	}

	public ConfigurationException(String message) {
		super(message);
	}

	public ConfigurationException(Throwable cause) {
		super(cause);
	}

}
