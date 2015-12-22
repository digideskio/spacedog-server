/**
 * © David Attias 2015
 */
package io.spacedog.utils;

import java.util.Date;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.common.base.Strings;

public class BackendKey {

	public static final String DEFAULT_BACKEND_KEY_NAME = "default";

	public String name;
	public String secret;
	public Date generatedAt;

	public BackendKey() {
		this(DEFAULT_BACKEND_KEY_NAME);
	}

	public BackendKey(String name) {
		this.name = name;
		this.secret = UUID.randomUUID().toString();
		this.generatedAt = new Date();
	}

	//
	// backend id utils
	//

	private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]{4,}");

	public static boolean isIdValid(String backendId) {

		if (!ID_PATTERN.matcher(backendId).matches())
			return false;

		if (backendId.indexOf("spacedog") > -1)
			return false;

		return true;
	}

	public static void checkIfIdIsValid(String backendId) {

		if (Strings.isNullOrEmpty(backendId))
			throw new IllegalArgumentException("backend id must not be null or empty");

		if (!isIdValid(backendId))
			throw new IllegalArgumentException("backend id must comply with these rules: "//
					+ "is at least 4 characters long, "//
					+ "is only composed of a-z and 0-9 characters, "//
					+ "is lowercase,  "//
					+ "does not conbtain 'spacedog'");
	}

}