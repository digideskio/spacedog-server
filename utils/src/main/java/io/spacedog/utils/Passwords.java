/**
 * © David Attias 2015
 */
package io.spacedog.utils;

import java.util.Optional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.bind.DatatypeConverter;

public class Passwords {

	public static final String PASSWORD_DEFAULT_REGEX = ".{6,}";

	public static String checkAndHash(String password) {
		return checkAndHash(password, Optional.empty());
	}

	public static String checkAndHash(String password, Optional<String> regex) {
		checkValid(password, regex);
		return hash(password);
	}

	public static void checkValid(String password) {
		checkValid(password, Optional.empty());
	}

	public static void checkValid(String password, Optional<String> regex) {
		Check.matchRegex(regex.orElse(PASSWORD_DEFAULT_REGEX), password, "password");
	}

	/**
	 * ******* README ******* For now, I use hard coded salt and iteration
	 * number (1000). If I decide to change this, I have to maintain this algo
	 * and har coded values until all password hashed this way are hashed anoter
	 * way. Salt and iterations could be saved in datastore close to the hashed
	 * password.
	 */
	public static String hash(String password) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), "hjyuetcslhhjgl".getBytes(), 1000, 64 * 8);
			SecretKeyFactory skf;
			skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			return DatatypeConverter.printHexBinary(skf.generateSecret(spec).getEncoded());
		} catch (Throwable t) {
			throw new RuntimeException("failed to hash password", t);
		}
	}

}
