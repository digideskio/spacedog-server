/**
 * © David Attias 2015
 */
package io.spacedog.watchdog;

import org.junit.Assert;
import org.junit.Test;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceClient.Backend;
import io.spacedog.client.SpaceClient.User;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.Json;
import io.spacedog.watchdog.SpaceSuite.TestOften;

@TestOften
public class CredentialsResourceTestOften extends Assert {

	@Test
	public void userIsSigningUpAndMore() throws Exception {

		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// fails since invalid bodies

		// empty user body
		SpaceRequest.post("/1/credentials/").backend(test)//
				.body(Json.object()).go(400);

		// no username
		SpaceRequest.post("/1/credentials/").backend(test)//
				.body("password", "hi titi", "email", "titi@dog.com").go(400);

		// no email
		SpaceRequest.post("/1/credentials/").backend(test)//
				.body("username", "titi", "password", "hi titi").go(400);

		// username too small
		SpaceRequest.post("/1/credentials/").backend(test)//
				.body("username", "ti", "password", "hi titi").go(400);

		// password too small
		SpaceRequest.post("/1/credentials/").backend(test)//
				.body("username", "titi", "password", "hi").go(400);

		// vince signs up
		User vince = SpaceClient.newCredentials(test, "vince", "hi vince", "vince@dog.com");

		// vince gets his credentials
		SpaceRequest.get("/1/credentials/vince").userAuth(vince).go(200)//
				.assertEquals("vince", "username")//
				.assertEquals("vince@dog.com", "email");

		// vince fails to get his credentials if wrong username
		SpaceRequest.get("/1/credentials/vince").basicAuth(test, "XXX", "hi vince").go(401);

		// vince fails to get his credentials if wrong password
		SpaceRequest.get("/1/credentials/vince").basicAuth(test, "vince", "XXX").go(401);

		// vince fails to get his credentials if wrong backend id
		SpaceRequest.get("/1/credentials/vince")//
				.basicAuth("XXX", vince.username, vince.password).go(401);

		// anonymous fails to get vince credentials
		SpaceRequest.get("/1/credentials/vince").backend(test).go(401);

		// another user fails to get vince credentials
		User fred = SpaceClient.newCredentials(test, "fred", "hi fred");
		SpaceRequest.get("/1/credentials/vince").userAuth(fred).go(401);

		// vince succeeds to login
		SpaceRequest.get("/1/login").userAuth(vince).go(200);

		// vince fails to login if wrong password
		SpaceRequest.get("/1/login").basicAuth(test, "vince", "XXX").go(401);
	}

	@Test
	public void deleteCredentials() throws Exception {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();
		User fred = SpaceClient.newCredentials(test, "fred", "hi fred");
		User vince = SpaceClient.newCredentials(test, "vince", "hi vince");

		// vince and fred can login
		SpaceRequest.get("/1/login").userAuth(vince).go(200);
		SpaceRequest.get("/1/login").userAuth(fred).go(200);

		// anonymous fails to deletes vince credentials
		SpaceRequest.delete("/1/credentials/vince").backend(test).go(401);

		// fred fails to delete vince credentials
		SpaceRequest.delete("/1/credentials/vince").userAuth(fred).go(401);

		// fred deletes his own credentials
		SpaceRequest.delete("/1/credentials/fred").userAuth(fred).go(200);

		// fred fails to login from now on
		SpaceRequest.get("/1/login").userAuth(fred).go(401);

		// admin deletes vince credentials
		SpaceRequest.delete("/1/credentials/vince").adminAuth(test).go(200);

		// vince fails to login from now on
		SpaceRequest.get("/1/login").userAuth(vince).go(401);
	}

	@Test
	public void setAndResetPassword() throws Exception {

		// prepare

		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();
		SpaceClient.newCredentials(test, "toto", "hi toto");

		// sign up without password should succeed

		String passwordResetCode = SpaceRequest.post("/1/credentials/").backend(test)//
				.body("username", "titi", "email", "titi@dog.com").go(201)//
				.assertNotNull("passwordResetCode")//
				.getFromJson("passwordResetCode")//
				.asText();

		// no password user login should fail
		// I can not pass a null password anyway to the basicAuth method

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "XXX").go(401);

		// no password user trying to create password with empty reset code
		// should fail

		SpaceRequest.post("/1/credentials/titi/password?passwordResetCode=").backend(test)//
				.field("password", "hi titi").go(400);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi").go(401);

		// no password user setting password with wrong reset code should fail

		SpaceRequest.post("/1/credentials/titi/password?passwordResetCode=XXX").backend(test)
				.field("password", "hi titi").go(400);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi").go(401);

		// titi inits its own password with right reset code should succeed

		SpaceRequest.post("/1/credentials/titi/password?passwordResetCode=" + passwordResetCode)//
				.backend(test).field("password", "hi titi").go(200);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi").go(200);

		// toto user changes titi password should fail

		SpaceRequest.put("/1/credentials/titi/password").basicAuth(test, "toto", "hi toto")//
				.field("password", "XXX").go(401);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "XXX").go(401);

		// titi changes its password should fail since password size < 6

		SpaceRequest.put("/1/credentials/titi/password").basicAuth(test, "titi", "hi titi")//
				.field("password", "XXX").go(400);

		// titi changes its password should succeed

		SpaceRequest.put("/1/credentials/titi/password").basicAuth(test, "titi", "hi titi")
				.field("password", "hi titi 2").go(200);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi 2").go(200);

		// login with old password should fail

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi").go(401);

		// admin user changes titi user password should succeed

		SpaceRequest.put("/1/credentials/titi/password").basicAuth(test, "test", "hi test")//
				.field("password", "hi titi 3").go(200);
		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi 3").go(200);

		// login with old password should fail

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi 2").go(401);

		// admin deletes titi password should succeed

		String newPasswordResetCode = SpaceRequest.delete("/1/credentials/titi/password")
				.basicAuth(test, "test", "hi test").go(200).getFromJson("passwordResetCode").asText();

		// titi login should fail

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi 3").go(401);

		// titi inits its password with old reset code should fail

		SpaceRequest.post("/1/credentials/titi/password?passwordResetCode=" + passwordResetCode)//
				.backend(test).field("password", "hi titi").go(400);

		// titi inits its password with new reset code should fail

		SpaceRequest.post("/1/credentials/titi/password?passwordResetCode=" + newPasswordResetCode)//
				.backend(test).field("password", "hi titi").go(200);

		SpaceRequest.get("/1/login").basicAuth(test, "titi", "hi titi").go(200);
	}

	@Test
	public void credentialsAreDeletedWhenBackendIsDeleted() throws Exception {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();
		User fred = SpaceClient.newCredentials(test, "fred", "hi fred");

		// fred can login
		SpaceRequest.get("/1/login").userAuth(fred).go(200);

		// admin deletes backend
		SpaceClient.deleteBackend(test);

		// fred fails to login since backend is no more
		SpaceRequest.get("/1/login").userAuth(fred).go(401);

		// admin creates backend with the same name
		SpaceClient.createBackend(test);

		// fred fails to login since backend is brand new
		SpaceRequest.get("/1/login").userAuth(fred).go(401);
	}
}