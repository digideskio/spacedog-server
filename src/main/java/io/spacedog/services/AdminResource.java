package io.spacedog.services;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import net.codestory.http.Context;
import net.codestory.http.annotations.Delete;
import net.codestory.http.annotations.Get;
import net.codestory.http.annotations.Post;
import net.codestory.http.annotations.Prefix;
import net.codestory.http.annotations.Put;
import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesAdminClient;
import org.elasticsearch.common.base.Strings;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;

import com.eclipsesource.json.JsonObject;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

@Prefix("/v1/admin")
public class AdminResource extends AbstractResource {

	// singleton begins

	private static AdminResource singleton = new AdminResource();

	static AdminResource get() {
		return singleton;
	}

	private AdminResource() {
	}

	// singleton ends

	public static final String SPACEDOG_INDEX = "spacedog";
	public static final String ACCOUNT_TYPE = "account";
	private static final Set<String> INTERNAL_INDICES = Sets
			.newHashSet(SPACEDOG_INDEX);

	public static final String SPACEDOG_KEY_HEADER = "x-spacedog-key";
	public static final String AUTHORIZATION_HEADER = "Authorization";
	public static final String BASIC_AUTHENTICATION_SCHEME = "Basic";

	public static final Charset UTF_8 = Charset.forName("UTF-8");

	void initSpacedogIndex() throws InterruptedException, ExecutionException,
			IOException {

		String accountMapping = Resources.toString(Resources
				.getResource("io/spacedog/services/account-mapping.json"),
				UTF_8);

		IndicesAdminClient indices = Start.getElasticClient().admin().indices();

		if (!indices.prepareExists(SPACEDOG_INDEX).get().isExists()) {
			indices.prepareCreate(SPACEDOG_INDEX)
					.addMapping(ACCOUNT_TYPE, accountMapping).get();
		}
	}

	/**
	 * Internal web service only accessible to administrators.
	 */
	@Get("/account")
	@Get("/account/")
	public Payload getAll(Context context) {
		try {
			checkAdminCredentialsOnly(context);

			SearchResponse response = Start.getElasticClient()
					.prepareSearch(SPACEDOG_INDEX).setTypes(ACCOUNT_TYPE)
					.setQuery(QueryBuilders.matchAllQuery()).get();

			return extractResults(response);
		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	@Get("/user/:username/check")
	@Get("/user/:username/check")
	public Payload checkUsername(String username) {
		return checkExistence(SPACEDOG_INDEX, ACCOUNT_TYPE, "username",
				username);
	}

	@Get("/backend/:id/check")
	@Get("/backend/:id/check")
	public Payload checkBackendId(String backendId) {
		return checkExistence(SPACEDOG_INDEX, ACCOUNT_TYPE, "backendId",
				backendId);
	}

	@Post("/account")
	@Post("/account/")
	public Payload signUp(String body, Context context) {
		try {
			JsonObject input = JsonObject.readFrom(body);

			Account account = new Account();
			account.backendId = input.getString("backendId", null);
			account.username = input.getString("username", null);
			account.email = input.getString("email", null);
			String password = input.getString("password", null);
			Account.checkPasswordValidity(password);
			account.hashedPassword = User.hashPassword(password);
			account.backendKey = new BackendKey();
			account.checkAccountInputValidity();

			if (ElasticHelper.search(SPACEDOG_INDEX, ACCOUNT_TYPE, "username",
					account.username).getTotalHits() > 0)
				return invalidParameters("username", account.username,
						String.format(
								"administrator username [%s] is not available",
								account.username));

			if (ElasticHelper.search(SPACEDOG_INDEX, ACCOUNT_TYPE, "backendId",
					account.backendId).getTotalHits() > 0)
				return invalidParameters("backendId", account.backendId,
						String.format("backend id [%s] is not available",
								account.backendId));

			byte[] accountBytes = getObjectMapper().writeValueAsBytes(account);
			Start.getElasticClient().prepareIndex(SPACEDOG_INDEX, ACCOUNT_TYPE)
					.setSource(accountBytes).get();

			// backend index is named after the backend id
			Start.getElasticClient()
					.admin()
					.indices()
					.prepareCreate(account.backendId)
					.addMapping(UserResource.USER_TYPE,
							UserResource.getDefaultUserMapping()).get();

			return created("/v1/admin", ACCOUNT_TYPE, account.backendId)
					.withHeader(AdminResource.SPACEDOG_KEY_HEADER,
							account.defaultClientKey());

		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	/**
	 * Internal web service only accessible to administrators.
	 */
	@Get("/account/:id")
	@Get("/account/:id/")
	public Payload get(String backendId, Context context) {
		try {
			checkAdminCredentialsOnly(context);

			GetResponse response = Start.getElasticClient()
					.prepareGet(SPACEDOG_INDEX, ACCOUNT_TYPE, backendId).get();

			if (!response.isExists())
				return error(HttpStatus.NOT_FOUND,
						"account with id [%s] not found", backendId);

			return new Payload(JSON_CONTENT, response.getSourceAsBytes(),
					HttpStatus.OK);
		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	/**
	 * Internal web service only accessible to administrators.
	 */
	@Put("/account/:id")
	@Put("/account/:id/")
	public Payload put(String id, String body, Context context) {
		try {
			checkAdminCredentialsOnly(context);

			return new Payload(HttpStatus.NOT_IMPLEMENTED);

		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	/**
	 * Internal web service only accessible to administrators.
	 */
	@Delete("/account/:id")
	@Delete("/account/:id/")
	public Payload delete(String backendId, Context context) {
		try {
			checkAdminCredentialsOnly(context);

			DeleteResponse resp1 = Start.getElasticClient()
					.prepareDelete(SPACEDOG_INDEX, ACCOUNT_TYPE, backendId)
					.get();

			if (!resp1.isFound())
				return error(HttpStatus.NOT_FOUND,
						"account with id [%s] not found", backendId);

			DeleteIndexResponse resp2 = Start.getElasticClient().admin()
					.indices().prepareDelete(backendId).get();

			if (!resp2.isAcknowledged())
				return error(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"internal index deletion not acknowledged for account and backend with id [%s] ",
						backendId);

			return success();
		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	/**
	 * Internal web service only accessible to administrators.
	 */
	@Get("/login")
	@Get("/login/")
	public Payload login(Context context) {
		try {
			Account account = checkAdminCredentialsOnly(context);

			return Payload.ok().withHeader(AdminResource.SPACEDOG_KEY_HEADER,
					account.defaultClientKey());
		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	public static Credentials checkCredentials(Context context)
			throws JsonParseException, JsonMappingException, IOException {

		String rawBackendKey = context.header(SPACEDOG_KEY_HEADER);

		if (Strings.isNullOrEmpty(rawBackendKey)) {
			Account account = checkAdminCredentialsOnly(context);
			return new Credentials(account.backendId, account.adminUser());

		} else {
			return checkUserCredentialsOnly(context);
		}
	}

	protected static Credentials checkUserCredentialsOnly(Context context)
			throws IOException, JsonParseException, JsonMappingException {

		String rawBackendKey = context.header(SPACEDOG_KEY_HEADER);

		String[] key = rawBackendKey.split(":", 3);

		if (key.length < 3)
			throw new AuthenticationException(
					"malformed backend key [%s], should be <backend-id>:<key-name>:<key-secret>",
					rawBackendKey);

		String backendId = key[0];
		String keyName = key[1];
		String keySecret = key[2];

		if (Strings.isNullOrEmpty(backendId))
			throw new AuthenticationException(
					"invalid backend key [%s], no backend id specified",
					rawBackendKey);

		if (INTERNAL_INDICES.contains(backendId))
			throw new AuthenticationException(
					"this backend id [%s] is reserved", backendId);

		if (Strings.isNullOrEmpty(keyName))
			throw new AuthenticationException(
					"invalid backend key [%s], no key name specified",
					rawBackendKey);

		if (Strings.isNullOrEmpty(keySecret))
			throw new AuthenticationException(
					"invalid backend key [%s], no key secret specified",
					rawBackendKey);

		// check client id/secret pairs in spacedog
		// index account objects

		SearchHits accountHits = ElasticHelper.search(SPACEDOG_INDEX,
				ACCOUNT_TYPE, "backendId", backendId, "backendKey.name",
				keyName, "backendKey.secret", keySecret);

		if (accountHits.getTotalHits() == 0)
			throw new AuthenticationException("invalid backend key [%s]",
					rawBackendKey);

		if (accountHits.getTotalHits() > 1)
			throw new RuntimeException(
					String.format(
							"more than one backend key for backend id [%s] and key name [%s]",
							backendId, keyName));

		Optional<String[]> tokens = decodeAuthorizationHeader(context
				.header(AUTHORIZATION_HEADER));

		if (tokens.isPresent()) {

			// check users in user specific backend index

			SearchHits userHits = ElasticHelper.search(backendId,
					UserResource.USER_TYPE, "username", tokens.get()[0],
					"hashedPassword", User.hashPassword(tokens.get()[1]));

			if (userHits.getTotalHits() == 0)
				throw new AuthenticationException(
						"invalid username or password");

			if (userHits.getTotalHits() > 1)
				throw new RuntimeException(String.format(
						"more than one user with username [%s]",
						tokens.get()[0]));

			return new Credentials(backendId, getObjectMapper().readValue(
					userHits.getAt(0).getSourceRef().array(), User.class));

		} else {

			Account account = getObjectMapper().readValue(
					accountHits.getAt(0).getSourceAsString(), Account.class);

			return new Credentials(backendId, account.backendKey);
		}
	}

	public static Account checkAdminCredentialsOnly(Context context)
			throws JsonParseException, JsonMappingException, IOException {

		Optional<String[]> tokens = decodeAuthorizationHeader(context
				.header(AUTHORIZATION_HEADER));

		if (tokens.isPresent()) {

			// check admin users in spacedog index

			SearchHits accountHits = ElasticHelper.search(SPACEDOG_INDEX,
					ACCOUNT_TYPE, "username", tokens.get()[0],
					"hashedPassword", User.hashPassword(tokens.get()[1]));

			if (accountHits.getTotalHits() == 0)
				throw new AuthenticationException(
						"invalid administrator username or password");

			if (accountHits.getTotalHits() > 1)
				throw new RuntimeException(String.format(
						"more than one admin user with username [%s]",
						tokens.get()[0]));

			return getObjectMapper().readValue(
					accountHits.getAt(0).getSourceAsString(), Account.class);

		} else
			throw new AuthenticationException(
					String.format(
							"not authorized since no 'Authorization' or '%s' header found",
							SPACEDOG_KEY_HEADER));
	}

	public static Optional<String[]> decodeAuthorizationHeader(
			String authzHeaderValue) {

		if (Strings.isNullOrEmpty(authzHeaderValue))
			return Optional.empty();

		String[] schemeAndTokens = authzHeaderValue.split(" ", 2);

		if (schemeAndTokens.length != 2)
			throw new AuthenticationException("invalid authorization header");

		if (Strings.isNullOrEmpty(schemeAndTokens[0]))
			throw new AuthenticationException(
					"no authorization scheme specified");

		if (!schemeAndTokens[0].equalsIgnoreCase(BASIC_AUTHENTICATION_SCHEME))
			throw new AuthenticationException(
					"authorization scheme [%s] not supported",
					schemeAndTokens[0]);

		byte[] encodedBytes = schemeAndTokens[1].getBytes(UTF_8);

		String decoded = null;

		try {
			decoded = new String(Base64.getDecoder().decode(encodedBytes));
		} catch (IllegalArgumentException e) {
			throw new AuthenticationException(
					"authorization token is not base 64 encoded", e);
		}

		String[] tokens = decoded.split(":", 2);

		if (tokens.length != 2)
			throw new AuthenticationException("invalid authorization token");

		if (Strings.isNullOrEmpty(tokens[1]))
			throw new AuthenticationException("no password specified");

		return Optional.of(tokens);
	}
}