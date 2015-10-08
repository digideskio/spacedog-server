package io.spacedog.services;

import io.spacedog.services.Account.InvalidAccountException;

import java.util.List;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.xml.bind.DatatypeConverter;

import com.google.common.base.Strings;

public class User {

	public String username;
	public String email;
	public String hashedPassword;
	public List<String> groups;
	public Meta meta;

	public void checkUserInputValidity() {
		if (Strings.isNullOrEmpty(email))
			throw new InvalidAccountException("user email is null or empty");
		if (Strings.isNullOrEmpty(username))
			throw new InvalidAccountException("user name is null or empty");
		if (Strings.isNullOrEmpty(hashedPassword))
			throw new InvalidAccountException("password is null or empty");
	}

	/**
	 * ******* README ******* For now, I use hard coded salt and iteration
	 * number (1000). If I decide to change this, I have to maintain this algo
	 * and har coded values until all password hashed this way are hashed anoter
	 * way. Salt and iterations could be saved in datastore close to the hashed
	 * password.
	 */
	public static String hashPassword(String password) {
		try {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),
					"hjyuetcslhhjgl".getBytes(), 1000, 64 * 8);
			SecretKeyFactory skf;
			skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			return DatatypeConverter.printHexBinary(skf.generateSecret(spec)
					.getEncoded());
		} catch (Throwable t) {
			throw new RuntimeException("failed to hash password", t);
		}
	}

	public static void checkPasswordValidity(String password) {
		if (Strings.isNullOrEmpty(password))
			throw new InvalidAccountException("password is null or empty");
	}
}
