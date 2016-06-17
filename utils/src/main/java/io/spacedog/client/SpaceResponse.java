/**
 * © David Attias 2015
 */
package io.spacedog.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.joda.time.DateTime;
import org.junit.Assert;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;
import com.google.common.collect.Iterators;
import com.google.common.io.ByteStreams;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;

import io.spacedog.utils.Json;
import io.spacedog.utils.SpaceHeaders;
import io.spacedog.utils.Utils;

public class SpaceResponse {

	private HttpRequest httpRequest;
	private HttpResponse<String> httpResponse;
	private DateTime before;
	private JsonNode jsonResponseContent;
	private long duration;

	public SpaceResponse(HttpRequest request, JsonNode jsonRequestContent, boolean debug)
			throws JsonProcessingException, IOException, UnirestException {

		this.httpRequest = request;

		if (debug) {
			Utils.info();
			Utils.infoNoLn("%s %s => ", httpRequest.getHttpMethod(), httpRequest.getUrl());
		}

		this.before = DateTime.now();

		httpResponse = httpRequest.asString();
		this.duration = DateTime.now().getMillis() - this.before.getMillis();

		if (debug) {
			Utils.info("%s (%s)", httpResponse.getStatus(), httpResponse.getStatusText());

			httpRequest.getHeaders().forEach((key, value) -> printHeader(key, value));

			if (request.getBody() != null //
					&& request.getBody().getEntity().getContentType() != null)
				Utils.info("content-type:" + request.getBody().getEntity().getContentType());

			if (jsonRequestContent != null)
				Utils.info("Request content: %s", Json.toPrettyString(jsonRequestContent));
		}

		String body = httpResponse.getBody();
		if (Json.isJson(body))
			jsonResponseContent = Json.readNode(body);

		if (debug) {

			Utils.info("=> duration: [%sms]", duration);

			httpResponse.getHeaders().forEach((key, value) -> Utils.info("=> %s: %s", key, value));

			if (body != null) {
				String prettyBody = jsonResponseContent != null //
						? Json.toPrettyString(jsonResponseContent) //
						: body.length() < 550 ? body : body.substring(0, 500) + " ...";

				Utils.info("=> Response body: %s", prettyBody);
			}
		}
	}

	private static void printHeader(String key, List<String> value) {
		if (key.equals(SpaceHeaders.AUTHORIZATION)) {
			String[] schemeAndTokens = value.get(0).split(" ", 2);
			String decoded = new String(Base64.getDecoder().decode(//
					schemeAndTokens[1].getBytes(Utils.UTF8)));
			String[] tokens = decoded.split(":", 2);
			Utils.info("%s: %s (%s)", key, value, tokens[0]);
		} else
			Utils.info("%s: %s", key, value);
	}

	public DateTime before() {
		return before;
	}

	public boolean isJson() {
		return jsonResponseContent != null;
	}

	public JsonNode jsonNode() {
		if (jsonResponseContent == null)
			throw new IllegalStateException("no json yet");
		return jsonResponseContent;
	}

	public ObjectNode objectNode() {
		if (!jsonNode().isObject())
			throw new RuntimeException(String.format("not a json object but [%s]", jsonResponseContent.getNodeType()));
		return (ObjectNode) jsonResponseContent;
	}

	public ArrayNode arrayNode() {
		if (!jsonNode().isArray())
			throw new RuntimeException(String.format("not a json array but [%s]", jsonResponseContent.getNodeType()));
		return (ArrayNode) jsonResponseContent;
	}

	public byte[] bytes() {
		try {
			return ByteStreams.toByteArray(this.httpResponse.getRawBody());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String string() {
		return this.httpResponse.getBody();
	}

	public HttpRequest httpRequest() {
		return httpRequest;
	}

	public HttpResponse<String> httpResponse() {
		if (httpResponse == null)
			throw new IllegalStateException("no response yet");
		return httpResponse;
	}

	public JsonNode getFromJson(String jsonPath) {
		return Json.get(jsonResponseContent, jsonPath);
	}

	public JsonNode findValue(String fieldName) {
		return jsonResponseContent.findValue(fieldName);
	}

	public SpaceResponse assertEquals(String expected, String jsonPath) {
		Assert.assertEquals(expected, Json.get(jsonResponseContent, jsonPath).textValue());
		return this;
	}

	public SpaceResponse assertEquals(int expected, String jsonPath) {
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		Assert.assertEquals(expected, //
				node.isArray() || node.isObject() ? node.size() : node.intValue());
		return this;
	}

	public SpaceResponse assertEquals(long expected, String jsonPath) {
		Assert.assertEquals(expected, Json.get(jsonResponseContent, jsonPath).longValue());
		return this;
	}

	public SpaceResponse assertEquals(float expected, String jsonPath, float delta) {
		Assert.assertEquals(expected, Json.get(jsonResponseContent, jsonPath).floatValue(), delta);
		return this;
	}

	public SpaceResponse assertEquals(double expected, String jsonPath, double delta) {
		Assert.assertEquals(expected, Json.get(jsonResponseContent, jsonPath).doubleValue(), delta);
		return this;
	}

	public SpaceResponse assertEquals(DateTime expected, String jsonPath) {
		Assert.assertEquals(expected, DateTime.parse(Json.get(jsonResponseContent, jsonPath).asText()));
		return this;
	}

	public SpaceResponse assertEquals(JsonNode expected) {
		Assert.assertEquals(expected, jsonResponseContent);
		return this;
	}

	public SpaceResponse assertEquals(JsonNode expected, String jsonPath) {
		Assert.assertEquals(expected, Json.get(jsonResponseContent, jsonPath));
		return this;
	}

	public SpaceResponse assertEqualsWithoutMeta(JsonNode expected) {
		Assert.assertEquals(expected, objectNode().deepCopy().without("meta"));
		return this;
	}

	public SpaceResponse assertNotNull(String jsonPath) {
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (node == null || node.isNull())
			Assert.fail(String.format("json property [%s] is null", jsonPath));
		return this;
	}

	public SpaceResponse assertNotNullOrEmpty(String jsonPath) {
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (Strings.isNullOrEmpty(node.asText()))
			Assert.fail(String.format("json string property [%s] is null or empty", jsonPath));
		return this;
	}

	public SpaceResponse assertTrue(String jsonPath) {
		Assert.assertTrue(Json.get(jsonResponseContent, jsonPath).asBoolean());
		return this;
	}

	public SpaceResponse assertFalse(String jsonPath) {
		Assert.assertFalse(Json.get(jsonResponseContent, jsonPath).asBoolean());
		return this;
	}

	public SpaceResponse assertDateIsValid(String jsonPath) {
		assertNotNull(jsonPath);
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (node.isTextual()) {
			try {
				DateTime.parse(node.asText());
				return this;
			} catch (IllegalArgumentException e) {
			}
		}
		Assert.fail(String.format(//
				"json property [%s] with value [%s] is not a valid SpaceDog date", jsonPath, node));
		return this;
	}

	public SpaceResponse assertDateIsRecent(String jsonPath) {
		long now = DateTime.now().getMillis();
		assertDateIsValid(jsonPath);
		DateTime date = DateTime.parse(Json.get(jsonResponseContent, jsonPath).asText());
		if (date.isBefore(now - 3000) || date.isAfter(now + 3000))
			Assert.fail(String.format(//
					"json property [%s] with value [%s] is not a recent SpaceDog date (now +/- 3s)", jsonPath, date));
		return this;
	}

	public SpaceResponse assertSizeEquals(int size, String jsonPath) {
		assertJsonContent();
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (Json.isNull(node))
			Assert.fail(String.format("property [%s] is null", jsonPath));
		if (size != node.size())
			Assert.fail(String.format("expected size [%s], json property [%s] node size [%s]", //
					size, jsonPath, node.size()));
		return this;
	}

	public SpaceResponse assertSizeEquals(int size) {
		assertJsonContent();
		if (size != jsonResponseContent.size())
			Assert.fail(String.format("expected size [%s], root json node size [%s]", //
					size, jsonResponseContent.size()));
		return this;
	}

	public SpaceResponse assertJsonContent() {
		if (jsonResponseContent == null)
			Assert.fail("response content is not json formatted");
		return this;
	}

	public SpaceResponse assertContainsValue(String expected, String fieldName) {
		assertJsonContent();
		if (!jsonResponseContent.findValuesAsText(fieldName).contains(expected))
			Assert.fail(String.format("no field named [%s] found with value [%s]", fieldName, expected));
		return this;
	}

	public SpaceResponse assertContains(JsonNode expected) {
		assertJsonContent();
		if (!Iterators.contains(jsonResponseContent.elements(), expected))
			Assert.fail(String.format(//
					"response does not contain [%s]", expected));
		return this;
	}

	public SpaceResponse assertContains(JsonNode expected, String jsonPath) {
		assertJsonContent();
		if (!Iterators.contains(Json.get(jsonResponseContent, jsonPath).elements(), expected))
			Assert.fail(String.format(//
					"field [%s] does not contain [%s]", jsonPath, expected));
		return this;
	}

	public SpaceResponse assertNotPresent(String jsonPath) {
		assertJsonContent();
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (node != null)
			Assert.fail(String.format(//
					"json path [%s] contains [%s]", jsonPath, node));
		return this;
	}

	public SpaceResponse assertPresent(String jsonPath) {
		assertJsonContent();
		JsonNode node = Json.get(jsonResponseContent, jsonPath);
		if (node == null)
			Assert.fail(String.format(//
					"json path [%s] not found", jsonPath));
		return this;
	}

	public SpaceResponse assertHeaderEquals(String expected, String headerName) {
		// header name is converted to lower case to get around Unirest bug
		// where header names are only lower cased
		String lowerCasedHeaderName = headerName.toLowerCase();

		if (!Arrays.asList(expected).equals(//
				httpResponse.getHeaders().get(lowerCasedHeaderName)))
			Assert.fail(String.format("response header [%s] not equal to [%s] but to %s", //
					headerName, expected, httpResponse.getHeaders().get(lowerCasedHeaderName)));

		return this;
	}

	public SpaceResponse assertHeaderContains(String expected, String headerName) {
		// header name is converted to lower case to get around Unirest bug
		// where header names are only lower cased
		String lowerCasedHeaderName = headerName.toLowerCase();

		List<String> headerValue = httpResponse.getHeaders().get(lowerCasedHeaderName);

		if (headerValue == null)
			Assert.fail(String.format("no response header with name [%s]", headerName));

		if (!headerValue.contains(expected))
			Assert.fail(String.format("response header with name [%s] does not contain [%s] but %s", //
					headerName, expected, headerValue));

		return this;
	}

	public void assertBodyEquals(String expected) {
		Assert.assertEquals(expected, string());
	}
}
