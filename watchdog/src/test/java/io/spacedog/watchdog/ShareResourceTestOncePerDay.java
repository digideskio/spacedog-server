package io.spacedog.watchdog;

import java.util.Arrays;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceClient.Backend;
import io.spacedog.client.SpaceClient.User;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.Json;
import io.spacedog.utils.SpaceHeaders;
import io.spacedog.watchdog.SpaceSuite.TestOncePerDay;

@TestOncePerDay
public class ShareResourceTestOncePerDay {

	private static final String FILE_CONTENT = "This is a test file!";

	@Test
	public void shareListAndGetFiles() throws Exception {

		// prepare
		Backend testBackend = SpaceClient.resetTestBackend();
		SpaceClient.initUserDefaultSchema(testBackend);
		User vince = SpaceClient.createUser(testBackend, "vince", "hi vince", "vince@dog.com");
		User fred = SpaceClient.createUser(testBackend, "fred", "hi fred", "fred@dog.com");

		// only admin can get all shared locations
		SpaceRequest.get("/1/share").go(401);
		SpaceRequest.get("/1/share").backend(testBackend).go(401);
		SpaceRequest.get("/1/share").userAuth(vince).go(401);

		// this account is brand new, no shared files
		SpaceRequest.get("/1/share").adminAuth(testBackend).go(200)//
				.assertSizeEquals(0, "results");

		// anonymous users are not allowed to share files
		SpaceRequest.put("/1/share/tweeter.png").go(401);
		SpaceRequest.put("/1/share/tweeter.png").backend(testBackend).go(401);

		// vince shares a small png file
		byte[] pngBytes = Resources.toByteArray(//
				Resources.getResource("io/spacedog/watchdog/tweeter.png"));

		JsonNode json = SpaceRequest.put("/1/share/tweeter.png")//
				.userAuth(vince)//
				.body(pngBytes)//
				.go(200).jsonNode();

		String pngPath = json.get("path").asText();
		String pngLocation = json.get("location").asText();
		String pngS3Location = json.get("s3").asText();

		// admin lists all shared files should return tweeter.png path only
		SpaceRequest.get("/1/share").adminAuth(testBackend).go(200)//
				.assertSizeEquals(1, "results")//
				.assertEquals(pngPath, "results.0.path");

		// anonymous gets shared file with its location
		byte[] downloadedBytes = SpaceRequest.get(pngLocation).go(200)//
				.assertHeaderEquals("gzip", SpaceHeaders.CONTENT_ENCODING)//
				.assertHeaderEquals("image/png", SpaceHeaders.CONTENT_TYPE)//
				.assertHeaderEquals("vince", SpaceHeaders.SPACEDOG_OWNER)//
				.bytes();

		Assert.assertTrue(Arrays.equals(pngBytes, downloadedBytes));

		// download shared png file through S3 direct access
		downloadedBytes = SpaceRequest.get(pngS3Location).go(200)//
				.assertHeaderEquals("image/png", SpaceHeaders.CONTENT_TYPE)//
				.bytes();

		Assert.assertTrue(Arrays.equals(pngBytes, downloadedBytes));

		// share small text file
		json = SpaceRequest.put("/1/share/test.txt")//
				.userAuth(fred)//
				.body(FILE_CONTENT.getBytes())//
				.go(200)//
				.jsonNode();

		String txtPath = json.get("path").asText();
		String txtLocation = json.get("location").asText();
		String txtS3Location = json.get("s3").asText();

		// list all shared files should return 2 paths
		// get first page with only one path
		json = SpaceRequest.get("/1/share?size=1")//
				.adminAuth(testBackend)//
				.go(200)//
				.assertSizeEquals(1, "results")//
				.jsonNode();

		Set<String> all = Sets.newHashSet(Json.get(json, "results.0.path").asText());

		// get the index of the next page in the first request response
		String next = json.get("next").asText();

		// get second (and last) page with only one path
		json = SpaceRequest.get("/1/share?size=1&next=" + next)//
				.adminAuth(testBackend)//
				.go(200)//
				.assertSizeEquals(1, "results")//
				.assertNotPresent("next")//
				.jsonNode();

		// the set should contain both file paths
		all.add(Json.get(json, "results.0.path").asText());
		Assert.assertTrue(all.contains(pngPath));
		Assert.assertTrue(all.contains(txtPath));

		// download shared text file
		String stringContent = SpaceRequest.get(txtLocation).backend(testBackend).go(200)//
				.assertHeaderEquals("gzip", SpaceHeaders.CONTENT_ENCODING)//
				.assertHeaderEquals("text/plain", SpaceHeaders.CONTENT_TYPE)//
				.assertHeaderEquals("fred", SpaceHeaders.SPACEDOG_OWNER)//
				.httpResponse().getBody();

		Assert.assertEquals(FILE_CONTENT, stringContent);

		// download shared text file through direct S3 access
		stringContent = SpaceRequest.get(txtS3Location).go(200)//
				.assertHeaderEquals("text/plain", SpaceHeaders.CONTENT_TYPE)//
				.httpResponse().getBody();

		Assert.assertEquals(FILE_CONTENT, stringContent);

		// only admin or owner can delete a shared file
		SpaceRequest.delete(txtLocation).go(401);
		SpaceRequest.delete(txtLocation).backend(testBackend).go(401);
		SpaceRequest.delete(txtLocation).userAuth(vince).go(401);

		// owner (fred) can delete its own shared file (test.txt)
		SpaceRequest.delete(txtLocation).userAuth(fred).go(200);

		// list of shared files should only return the png file path
		SpaceRequest.get("/1/share").adminAuth(testBackend).go(200)//
				.assertSizeEquals(1, "results")//
				.assertEquals(pngPath, "results.0.path");

		// only admin can delete all shared files
		SpaceRequest.delete("/1/share").go(401);
		SpaceRequest.delete("/1/share").backend(testBackend).go(401);
		SpaceRequest.delete("/1/share").userAuth(fred).go(401);
		SpaceRequest.delete("/1/share").userAuth(vince).go(401);
		SpaceRequest.delete("/1/share").adminAuth(testBackend).go(200)//
				.assertSizeEquals(1, "deleted")//
				.assertContains(TextNode.valueOf(pngPath), "deleted");

		SpaceRequest.get("/1/share").adminAuth(testBackend).go(200)//
				.assertSizeEquals(0, "results");

	}

	void upload(String putUrl, String content, String contentType, String fileName, String username, String userType)
			throws UnirestException {
		HttpResponse<String> response = Unirest.put(putUrl)//
				.header("x-amz-meta-username", username)//
				.header("x-amz-meta-user-type", userType)//
				.header("Content-Type", contentType)//
				.header("Content-Disposition", String.format("attachment; filename=\"%s\"", fileName))//
				.body(content.getBytes())//
				.asString();

		System.out.println();
		System.out.println(String.format("PUT %s => %s %s", //
				putUrl, response.getStatus(), response.getStatusText()));
		System.out.println("Response body = " + response.getBody());

		Assert.assertEquals(200, response.getStatus());
	}
}