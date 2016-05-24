package io.spacedog.watchdog;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceClient.Backend;
import io.spacedog.client.SpaceRequest;
import io.spacedog.client.SpaceResponse;

public class SnapshotResourceTest extends Assert {

	@Test
	public void snapshotAndRestoreMultipleTimes() throws Exception {

		// prepare
		SpaceClient.prepareTest();
		Backend aaaaBackend = new Backend("aaaa", "aaaa", "hi aaaa", "aaaa@dog.com");
		Backend bbbbBackend = new Backend("bbbb", "bbbb", "hi bbbb", "bbbb@dog.com");
		Backend ccccBackend = new Backend("cccc", "cccc", "hi cccc", "cccc@dog.com");
		SpaceClient.deleteBackend(aaaaBackend);
		SpaceClient.deleteBackend(bbbbBackend);
		SpaceClient.deleteBackend(ccccBackend);

		// creates backend and user
		SpaceClient.createBackend(aaaaBackend);
		SpaceClient.initUserDefaultSchema(aaaaBackend);
		SpaceClient.createUser(aaaaBackend, "vince", "hi vince");
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(200);

		// deletes the current repository to force repo creation by this test
		// use full url to avoid delete by mistake any prod repo
		String repository = DateTime.now().withZone(DateTimeZone.UTC).toString("yyyy-ww");
		SpaceRequest.delete("http://localhost:9200/_snapshot/{repoId}")//
				.routeParam("repoId", repository)//
				.go(200, 404);

		// first snapshot (returns 202 since wait for completion false)
		String firstSnapId = SpaceRequest.post("/1/snapshot")//
				.superdogAuth()//
				.go(202)//
				.getFromJson("id")//
				.asText();

		// fails since snapshot is not yet completed
		// returns 400 if not yet restorable or if restoreInfo is null
		SpaceResponse response = SpaceRequest.post("/1/snapshot/latest/restore")//
				.superdogAuth()//
				.go(400);

		// poll and wait for snapshot to complete
		do {

			response = SpaceRequest.get("/1/snapshot/latest")//
					.superdogAuth()//
					.go(200)//
					.assertEquals(firstSnapId, "id");

			// let server work a bit
			Thread.sleep(100);

		} while (!response.jsonNode().get("state").asText().equalsIgnoreCase("SUCCESS"));

		ObjectNode firstSnap = response.objectNode();

		// gets snapshot by id
		SpaceRequest.get("/1/snapshot/" + firstSnapId)//
				.superdogAuth()//
				.go(200)//
				.assertEquals(firstSnap);

		// creates another backend and user
		SpaceClient.createBackend(bbbbBackend);
		SpaceClient.initUserDefaultSchema(bbbbBackend);
		SpaceClient.createUser(bbbbBackend, "fred", "hi fred");
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(200);

		// second snapshot (returns 201 since wait for completion true, 202
		// otherwise)
		response = SpaceRequest.post("/1/snapshot")//
				.superdogAuth()//
				.queryString("waitForCompletion", "true")//
				.go(201)//
				.assertEquals(repository, "snapshot.repository")//
				.assertEquals("SUCCESS", "snapshot.state")//
				.assertEquals("all", "snapshot.type");

		ObjectNode secondSnap = (ObjectNode) response.getFromJson("snapshot");
		String secondSnapId = response.getFromJson("id").asText();

		SpaceRequest.get("/1/snapshot")//
				.superdogAuth()//
				.go(200)//
				.assertEquals(secondSnap, "results.0")//
				.assertEquals(firstSnap, "results.1");

		// create another account and add a user
		SpaceClient.createBackend(ccccBackend);
		SpaceClient.initUserDefaultSchema(ccccBackend);
		SpaceClient.createUser(ccccBackend, "nath", "hi nath");
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(200);

		// third snapshot (returns 200 since wait for completion true, 202
		// otherwise)
		response = SpaceRequest.post("/1/snapshot")//
				.superdogAuth()//
				.queryString("waitForCompletion", "true")//
				.go(201)//
				.assertEquals(repository, "snapshot.repository")//
				.assertEquals("SUCCESS", "snapshot.state")//
				.assertEquals("all", "snapshot.type");

		ObjectNode thirdSnap = (ObjectNode) response.getFromJson("snapshot");
		String thirdSnapId = response.getFromJson("id").asText();

		SpaceRequest.get("/1/snapshot")//
				.superdogAuth()//
				.go(200)//
				.assertEquals(thirdSnap, "results.0")//
				.assertEquals(secondSnap, "results.1")//
				.assertEquals(firstSnap, "results.2");

		// restore to oldest snapshot
		SpaceRequest.post("/1/snapshot/{id}/restore")//
				.routeParam("id", firstSnapId)//
				.queryString("waitForCompletion", "true")//
				.superdogAuth()//
				.go(200);

		// check only account aaaa and user vince are present
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(200);
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(401);
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(401);

		// restore to second (middle) snapshot
		SpaceRequest.post("/1/snapshot/{id}/restore")//
				.routeParam("id", secondSnapId)//
				.queryString("waitForCompletion", "true")//
				.superdogAuth()//
				.go(200);

		// check only aaaa and bbbb accounts are present
		// check only vince and fred users are present
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(200);
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(200);
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(401);

		// restore to latest (third) snapshot
		SpaceRequest.post("/1/snapshot/{id}/restore")//
				.routeParam("id", thirdSnapId)//
				.queryString("waitForCompletion", "true")//
				.superdogAuth()//
				.go(200);

		// check all accounts and users are present
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(200);
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(200);
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(200);

		// delete all accounts and internal indices
		SpaceRequest.delete("/1/backend").adminAuth(aaaaBackend).go(200);
		SpaceRequest.delete("/1/backend").adminAuth(bbbbBackend).go(200);
		SpaceRequest.delete("/1/backend").adminAuth(ccccBackend).go(200);

		// check all accounts are deleted
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(401);
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(401);
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(401);

		// restore to latest (third) snapshot
		SpaceRequest.post("/1/snapshot/latest/restore")//
				.queryString("waitForCompletion", "true")//
				.superdogAuth()//
				.go(200);

		// check all accounts and users are back
		SpaceRequest.get("/1/user/vince").adminAuth(aaaaBackend).go(200);
		SpaceRequest.get("/1/user/fred").adminAuth(bbbbBackend).go(200);
		SpaceRequest.get("/1/user/nath").adminAuth(ccccBackend).go(200);

		// check that restore to an invalid snapshot id fails
		SpaceRequest.post("/1/snapshot/xxxx/restore")//
				.queryString("waitForCompletion", "true")//
				.superdogAuth()//
				.go(404);

		// check account administrator can not restore the platform
		SpaceRequest.post("/1/snapshot/latest/restore")//
				.adminAuth(aaaaBackend)//
				.go(401);

		// clean up
		SpaceRequest.delete("/1/backend").adminAuth(aaaaBackend).go(200);
		SpaceRequest.delete("/1/backend").adminAuth(bbbbBackend).go(200);
		SpaceRequest.delete("/1/backend").adminAuth(ccccBackend).go(200);

		// check snapshot list did not change since last snapshot
		SpaceRequest.get("/1/snapshot")//
				.superdogAuth()//
				.go(200)//
				.assertEquals(thirdSnap, "results.0")//
				.assertEquals(secondSnap, "results.1")//
				.assertEquals(firstSnap, "results.2");
	}
}