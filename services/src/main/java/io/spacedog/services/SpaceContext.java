package io.spacedog.services;

import java.util.Optional;

import com.google.common.net.HttpHeaders;

import io.spacedog.utils.AuthenticationException;
import io.spacedog.utils.AuthorizationHeader;
import io.spacedog.utils.Backends;
import io.spacedog.utils.Credentials;
import io.spacedog.utils.Exceptions;
import io.spacedog.utils.SpaceHeaders;
import net.codestory.http.Context;

/**
 * Context credentials should only be accessed from public static check methods
 * from this class.
 */
public class SpaceContext {

	private static ThreadLocal<SpaceContext> threadLocal = new ThreadLocal<SpaceContext>();

	private Context context;
	private boolean isTest;
	private Debug debug;
	private Credentials credentials;
	private boolean authorizationChecked;

	private SpaceContext(Context context) {
		this.context = context;
		this.isTest = Boolean.parseBoolean(context().header(SpaceHeaders.SPACEDOG_TEST));
		this.credentials = new Credentials(extractSubdomain(context));
		this.debug = new Debug(//
				Boolean.parseBoolean(context().header(SpaceHeaders.SPACEDOG_DEBUG)));
	}

	public Context context() {
		return context;
	}

	public static SpaceFilter filter() {

		// uri is already checked by SpaceFilter default matches method

		return (uri, context, nextFilter) -> {
			if (threadLocal.get() == null) {
				try {
					threadLocal.set(new SpaceContext(context));
					return nextFilter.get();

				} finally {
					threadLocal.set(null);
				}
			} else
				// means there is another filter higher in the stack managing
				// the space context
				return nextFilter.get();
		};
	}

	public static SpaceContext get() {
		SpaceContext context = threadLocal.get();
		if (context == null)
			throw Exceptions.runtime("no thread local context set");
		return context;
	}

	public static boolean isTest() {
		return get().isTest;
	}

	public static boolean isDebug() {
		return get().debug.isTrue();
	}

	public static Debug debug() {
		return get().debug;
	}

	public static String backendId() {
		return getCredentials().backendId();
	}

	//
	// Check credentials static methods
	//

	public static SpaceFilter checkAuthorizationFilter() {

		return (uri, context, nextFilter) -> {
			get().checkAuthorizationHeader();
			return nextFilter.get();
		};
	}

	public static Credentials checkSuperDogCredentials() {
		return checkSuperDogCredentials(true);
	}

	public static Credentials checkSuperDogCredentials(boolean checkCustomerBackend) {
		Credentials credentials = checkCredentials(checkCustomerBackend);
		if (credentials.isSuperDog())
			return credentials;
		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkSuperAdminCredentials() {
		return checkSuperAdminCredentials(true);
	}

	public static Credentials checkSuperAdminCredentials(boolean checkCustomerBackend) {
		Credentials credentials = checkCredentials(checkCustomerBackend);
		if (credentials.isAtLeastSuperAdmin())
			return credentials;
		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkAdminCredentials() {
		return checkAdminCredentials(true);
	}

	public static Credentials checkAdminCredentials(boolean checkCustomerBackend) {
		Credentials credentials = checkCredentials(checkCustomerBackend);
		if (credentials.isAtLeastAdmin())
			return credentials;
		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkUserOrAdminCredentials() {
		return checkUserOrAdminCredentials(true);
	}

	public static Credentials checkUserOrAdminCredentials(boolean checkCustomerBackend) {
		Credentials credentials = checkCredentials(checkCustomerBackend);
		if (credentials.isAtLeastAdmin() || credentials.isAtLeastUser())
			return credentials;
		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkUserCredentials() {
		return checkUserCredentials(true);
	}

	public static Credentials checkUserCredentials(String username) {
		Credentials credentials = checkUserCredentials(true);

		if (credentials.isAtLeastAdmin() || credentials.name().equals(username))
			return credentials;

		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkUserCredentials(boolean checkCustomerBackend) {
		Credentials credentials = checkCredentials(checkCustomerBackend);
		if (credentials.isAtLeastUser())
			return credentials;
		throw Exceptions.insufficientCredentials(credentials);
	}

	public static Credentials checkCredentials() {
		return checkCredentials(true);
	}

	public static Credentials checkCredentials(boolean checkCustomerBackend) {
		Credentials credentials = getCredentials();
		if (checkCustomerBackend && credentials.isRootBackend())
			throw Exceptions.illegalArgument("host doesn't specify any backend id");
		return credentials;
	}

	public static void setCredentials(Credentials credentials) {
		get().credentials = credentials;
	}

	public static Credentials getCredentials() {
		return get().credentials;
	}

	//
	// Implementation
	//

	private String extractSubdomain(Context context) {
		String host = context.request().header(HttpHeaders.HOST);
		String[] terms = host.split("\\.");
		return terms.length == 3 ? terms[0] : Backends.ROOT_API;
	}

	private void checkAuthorizationHeader() {
		if (!authorizationChecked) {
			authorizationChecked = true;
			SpaceContext.debug().credentialCheck();
			String backendId = backendId();
			String headerValue = context.header(SpaceHeaders.AUTHORIZATION);

			if (headerValue != null) {
				boolean superdog = false;
				Optional<Credentials> userCredentials = Optional.empty();
				AuthorizationHeader authHeader = new AuthorizationHeader(headerValue);
				if (authHeader.isBasic()) {
					superdog = authHeader.username().startsWith("superdog-");

					userCredentials = CredentialsResource.get().check(//
							superdog ? Backends.ROOT_API : backendId, //
							authHeader.username(), authHeader.password());

					if (!userCredentials.isPresent())
						throw new AuthenticationException("invalid username or password for backend [%s]", backendId);

				} else if (authHeader.isBearer()) {
					userCredentials = CredentialsResource.get().check(backendId, authHeader.token());

					if (!userCredentials.isPresent())
						throw new AuthenticationException("invalid access token for backend [%s]", backendId);
				}

				credentials = userCredentials.get();

				// sets superdog backend id to the request backend id
				if (superdog)
					credentials.backendId(backendId);
			}

		}
	}
}