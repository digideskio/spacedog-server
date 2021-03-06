/**
 * © David Attias 2015
 */
package io.spacedog.examples;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.http.client.utils.URIBuilder;
import org.junit.Test;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.CredentialsSettings;

public class Linkedin extends SpaceClient {

	// @Test
	public void getMyLinkedinProfil() {

		// prepare
		SpaceClient.prepareTest();

		// get my profil
		SpaceRequest.get("/1/linkedin/people/me/firstName,picture-url,location,summary")//
				.bearerAuth("XXXXXXXXXXXX")//
				.backendId("test")//
				.go(200);
	}

	@Test
	public void login() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// credentials settings with guest sign up enabled
		CredentialsSettings settings = defaultCredentialsSettings(false, test);
		SpaceClient.saveSettings(test, settings);

		// login succeeds
		linkedinLogin(test, false, settings);
	}

	@Test
	public void loginWithSpecificSessionMaximumLifetime() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// credentials settings with guest sign up enabled
		CredentialsSettings settings = defaultCredentialsSettings(false, test);
		settings.useLinkedinExpiresIn = false;
		settings.sessionMaximumLifetime = 10000;
		SpaceClient.saveSettings(test, settings);

		// expiresIn is between 9000 and 10000
		linkedinLogin(test, false, settings);
	}

	@Test
	public void loginFailsCauseGuestSignUpIsDisabled() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set credentials settings with guest sign up disabled
		CredentialsSettings settings = defaultCredentialsSettings(true, test);
		SpaceClient.saveSettings(test, settings);

		// login fails since guest sign in is disabled
		// and credentials has not been pre created by admin
		linkedinLogin(test, false, settings);
	}

	@Test
	public void loginSucceedsCauseCredentialsPreRegisteredByAdmin() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set credentials settings with guest sign up disabled
		CredentialsSettings settings = defaultCredentialsSettings(true, test);
		SpaceClient.saveSettings(test, settings);

		// admin pre registers some credentials for a new user
		// username must equals the linkedin account email
		SpaceRequest.post("/1/credentials").adminAuth(test)//
				.body("username", "attias666@gmail.com", "email", "attias666@gmail.com")//
				.go(201).assertPresent("passwordResetCode");

		// login succeeds
		// check credentialsId in json payload is identical
		// to credentialsId in previous request before login
		linkedinLogin(test, false, settings);
	}

	@Test
	public void loginFailsCauseInvalidSecret() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set credentials settings with guest sign up disabled
		CredentialsSettings settings = defaultCredentialsSettings(true, test);
		settings.linkedinSecret = "XXX";
		SpaceClient.saveSettings(test, settings);

		// login fails since secret is invalid
		// check error in response json payload
		linkedinLogin(test, false, settings);
	}

	@Test
	public void redirectedLogin() throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// credentials settings with guest sign up enabled
		CredentialsSettings settings = defaultCredentialsSettings(false, test);
		SpaceClient.saveSettings(test, settings);

		// login succeeds
		// check access token in redirect url params
		linkedinLogin(test, true, settings);
	}

	@Test
	public void redirectedLoginFailsCauseInvalidSecret() //
			throws URISyntaxException, IOException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set credentials settings with guest sign up disabled
		CredentialsSettings settings = defaultCredentialsSettings(true, test);
		settings.linkedinSecret = "XXX";
		SpaceClient.saveSettings(test, settings);

		// login fails since secret is invalid
		// check error in redirect url params
		linkedinLogin(test, true, settings);
	}

	private void linkedinLogin(Backend backend, boolean redirect, CredentialsSettings settings)
			throws URISyntaxException, IOException {

		// test redirect uri
		String redirectUri = SpaceRequest.configuration().target()//
				.url(backend.backendId, "/1/login/linkedin");

		if (redirect)
			redirectUri = redirectUri + "/redirect";

		// build the linkedin oauth2 signin url
		URIBuilder url = new URIBuilder("https://www.linkedin.com/oauth/v2/authorization");
		url.addParameter("response_type", "code");
		url.addParameter("state", "thisIsSpaceDog");
		url.addParameter("redirect_uri", redirectUri);
		url.addParameter("client_id", settings.linkedinId);

		// open a browser with the linkedin signin url
		// to start the linkedin oauth2 authentication process
		Runtime.getRuntime().exec("open " + url.toString());
	}

	private CredentialsSettings defaultCredentialsSettings(boolean disableGuestSignUp, Backend backend) {
		CredentialsSettings settings = new CredentialsSettings();
		settings.disableGuestSignUp = disableGuestSignUp;
		settings.linkedinId = SpaceRequest.configuration().testLinkedinClientId();
		settings.linkedinSecret = SpaceRequest.configuration().testLinkedinClientSecret();
		settings.linkedinFinalRedirectUri = SpaceRequest.configuration().target().url(backend.backendId);
		return settings;
	}
}
