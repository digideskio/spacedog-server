/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Maps;
import com.stripe.Stripe;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.APIException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Token;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.StripeSettings;

public class StripeResourceTest extends SpaceClient {

	@Test
	public void testStripeServices() throws AuthenticationException, InvalidRequestException, APIConnectionException,
			CardException, APIException {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();
		User david = SpaceClient.signUp(test, "david", "hi david");

		// set stripe settings
		StripeSettings settings = new StripeSettings();
		settings.secretKey = SpaceRequest.configuration().testStripeSecretKey();
		Stripe.apiKey = settings.secretKey;
		SpaceClient.saveSettings(test, settings);

		// david's stripe customer is not yet created
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david)//
				.go(404);

		// david creates his stripe customer with his card token
		ObjectNode stripeCustomer = SpaceRequest.post("/1/stripe/customers")//
				.userAuth(david)//
				.go(201)//
				.assertEquals("customer", "object")//
				.assertEquals("david@spacedog.io", "email")//
				.assertSizeEquals(0, "sources.data")//
				.objectNode();

		// david fails to create another stripe customer
		// since he's got one already
		SpaceRequest.post("/1/stripe/customers")//
				.userAuth(david)//
				.go(400);

		// david gets his stripe customer
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david)//
				.go(200)//
				.assertEquals(stripeCustomer);

		// david registers a first card
		String bnpCardId = SpaceRequest.post("/1/stripe/customers/me/sources")//
				.body("source", createCardToken().getId(), //
						"description", "bnp")//
				.userAuth(david).go(201)//
				.assertEquals("bnp", "metadata.description")//
				.getString("id");

		// check david has a bnp card
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david)//
				.go(200)//
				.assertEquals("david@spacedog.io", "email")//
				.assertSizeEquals(1, "sources.data")//
				.assertEquals(bnpCardId, "sources.data.0.id")//
				.assertEquals("bnp", "sources.data.0.metadata.description");

		// TODO charge the card a first time

		// david deletes his card from his stripe customer account
		SpaceRequest.delete("/1/stripe/customers/me/sources/" + bnpCardId)//
				.userAuth(david).go(200);

		// check david has no card anymore
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david)//
				.go(200)//
				.assertSizeEquals(0, "sources.data");

		// david deletes again his card to gets 404
		SpaceRequest.delete("/1/stripe/customers/me/sources/" + bnpCardId)//
				.userAuth(david).go(404);

		// david creates another card
		SpaceRequest.post("/1/stripe/customers/me/sources")//
				.body("source", createCardToken().getId(), //
						"description", "lcl")//
				.userAuth(david).go(201)//
				.assertEquals("lcl", "metadata.description");

		// check david has an lcl card
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david)//
				.go(200)//
				.assertEquals("david@spacedog.io", "email")//
				.assertSizeEquals(1, "sources.data")//
				.assertEquals("lcl", "sources.data.0.metadata.description");

		// david deletes his stripe customer account
		SpaceRequest.delete("/1/stripe/customers/me")//
				.userAuth(david).go(200);

		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// check david's stripe customer account is gone
		SpaceRequest.get("/1/stripe/customers/me")//
				.userAuth(david).go(404);
	}

	Token createCardToken() throws AuthenticationException, InvalidRequestException, APIConnectionException,
			CardException, APIException {
		Map<String, Object> tokenParams = Maps.newHashMap();
		Map<String, Object> cardParams = Maps.newHashMap();
		cardParams.put("number", "4242424242424242");
		cardParams.put("exp_month", 11);
		cardParams.put("exp_year", 2017);
		cardParams.put("cvc", "314");
		tokenParams.put("card", cardParams);
		Token token = Token.create(tokenParams);
		return token;
	}

}
