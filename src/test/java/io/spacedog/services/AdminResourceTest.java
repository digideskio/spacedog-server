/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.io.IOException;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.RequestBodyEntity;

public class AdminResourceTest extends AbstractTest {

	@Test
	public void shouldDeleteSignUpGetLoginTestAccount() throws UnirestException, InterruptedException, IOException {

		testAccount = resetTestAccount();

		// get just created test account should succeed

		GetRequest req1 = prepareGet("/v1/admin/account/test").basicAuth(testAccount.username, testAccount.password);
		JsonNode res1 = get(req1, 200).jsonNode();

		assertEquals("test", res1.get("backendId").asText());
		assertEquals("test", res1.get("username").asText());
		assertNotEquals("hi test", res1.get("hashedPassword").asText());
		assertEquals("david@spacedog.io", res1.get("email").asText());

		// create new account with same username should fail

		RequestBodyEntity req2 = preparePost("/v1/admin/account/")
				.body(Json.startObject().put("backendId", "anothertest").put("username", "test")
						.put("password", "hi test").put("email", "hello@spacedog.io").toString());

		JsonNode json2 = post(req2, 400).jsonNode();

		assertEquals("test", json2.get("invalidParameters").get("username").get("value").asText());

		// create new account with same backend id should fail

		RequestBodyEntity req2b = preparePost("/v1/admin/account/")
				.body(Json.startObject().put("backendId", "test").put("username", "anotheruser")
						.put("password", "hi anotheruser").put("email", "hello@spacedog.io").toString());

		JsonNode json2b = post(req2b, 400).jsonNode();

		assertEquals("test", json2b.get("invalidParameters").get("backendId").get("value").asText());

		// admin user login should succeed

		GetRequest req3 = prepareGet("/v1/admin/login").basicAuth("test", "hi test");
		String backendKey = get(req3, 200).response().getHeaders().get(AdminResource.BACKEND_KEY_HEADER).get(0);
		assertEquals(testAccount.backendKey, backendKey);

		// no header no user login should fail

		GetRequest req4 = prepareGet("/v1/admin/login");
		get(req4, 401);

		// invalid admin username login should fail

		GetRequest req5 = prepareGet("/v1/admin/login").basicAuth("XXX", "hi test");
		get(req5, 401);

		// invalid admin password login should fail

		GetRequest req6 = prepareGet("/v1/admin/login").basicAuth("test", "hi XXX");
		get(req6, 401);

		// data access with client key should succeed

		GetRequest req7 = prepareGet("/v1/data", testAccount.backendKey);
		JsonNode res7 = get(req7, 200).jsonNode();
		assertEquals(0, res7.get("total").asInt());

		// data access with admin user should succeed

		GetRequest req7b = prepareGet("/v1/data").basicAuth("test", "hi test");
		JsonNode res7b = get(req7b, 200).jsonNode();
		assertEquals(0, res7b.get("total").asInt());

		// data access with admin user but client key should fail

		GetRequest req8 = prepareGet("/v1/data", testAccount.backendKey).basicAuth("test", "hi test");
		get(req8, 401).jsonNode();

		// let's create a common user in 'test' backend

		RequestBodyEntity req9a = preparePost("/v1/user", testAccount.backendKey).body(Json.startObject()
				.put("username", "john").put("password", "hi john").put("email", "john@dog.io").toString());
		post(req9a, 201);

		refreshIndex("test");

		// data access with common user but no client key should fail

		GetRequest req9b = prepareGet("/v1/data").basicAuth("john", "hi john");
		get(req9b, 401);

		// admin access with regular user and backend key should fail

		GetRequest req10 = prepareGet("/v1/admin/account/test", testAccount.backendKey).basicAuth("john", "hi john");
		get(req10, 401);

	}

	public static ClientAccount resetTestAccount() throws UnirestException, IOException {
		return resetAccount("test", "test", "hi test", "david@spacedog.io");
	}

	public static ClientAccount resetAccount(String backendId, String username, String password, String email)
			throws UnirestException, IOException {

		HttpRequestWithBody req1 = prepareDelete("/v1/admin/account/{backendId}").routeParam("backendId", backendId)
				.basicAuth(username, password);

		// 401 Unauthorized is valid since if this account does not exist
		// delete returns 401 because admin username and password
		// won't match any account
		delete(req1, 200, 401);

		refreshIndex(AdminResource.ADMIN_INDEX);
		refreshIndex(backendId);

		RequestBodyEntity req2 = preparePost("/v1/admin/account/").body(Json.startObject().put("backendId", backendId)
				.put("username", username).put("password", password).put("email", email).toString());

		String backendKey = post(req2, 201).response().getHeaders().get(AdminResource.BACKEND_KEY_HEADER).get(0);

		assertFalse(Strings.isNullOrEmpty(backendKey));

		refreshIndex(AdminResource.ADMIN_INDEX);
		refreshIndex(backendId);

		return new ClientAccount(backendId, username, password, email, backendKey);
	}

	public static class ClientAccount {
		String backendId;
		String backendKey;
		String username;
		String password;
		String email;

		public ClientAccount(String backendId, String username, String password, String email, String backendKey) {
			this.backendId = backendId;
			this.backendKey = backendKey;
			this.username = username;
			this.password = password;
			this.email = email;
		}
	}

	private static ClientAccount testAccount;
}
