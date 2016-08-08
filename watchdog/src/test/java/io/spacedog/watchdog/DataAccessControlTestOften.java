/**
 * © David Attias 2015
 */
package io.spacedog.watchdog;

import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import com.beust.jcommander.internal.Maps;
import com.google.common.collect.Sets;

import io.spacedog.client.SpaceClient;
import io.spacedog.client.SpaceClient.Backend;
import io.spacedog.client.SpaceClient.User;
import io.spacedog.client.SpaceRequest;
import io.spacedog.utils.DataAclSettings;
import io.spacedog.utils.DataPermission;
import io.spacedog.utils.Json;
import io.spacedog.utils.Schema;
import io.spacedog.utils.Schema.SchemaAclSettings;
import io.spacedog.watchdog.SpaceSuite.TestOften;

@TestOften
public class DataAccessControlTestOften extends Assert {

	@Test
	public void testSchemaAclManagement() throws Exception {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set message schema with no acl settings
		SpaceClient.setSchema(//
				Schema.builder("message").text("text").build(), test);

		// get and check schema acl settings
		String body = SpaceRequest.get("/1/settings/acl")//
				.backend(test).go(200).httpResponse().getBody();
		DataAclSettings acl = Json.mapper().readValue(//
				body, DataAclSettings.class);

		assertEquals(1, acl.size());
		SchemaAclSettings messageAcl = acl.get("message");
		assertEquals(3, messageAcl.size());
		assertEquals(Sets.newHashSet(DataPermission.read_all), messageAcl.get("key"));
		assertEquals(Sets.newHashSet(DataPermission.create, DataPermission.update, //
				DataPermission.search, DataPermission.delete), messageAcl.get("user"));
		assertEquals(Sets.newHashSet(DataPermission.create, DataPermission.update_all, //
				DataPermission.search, DataPermission.delete_all), messageAcl.get("admin"));

		// set message schema new acl settings
		SpaceClient.setSchema(//
				Schema.builder("message").text("text")//
						.acl("admin", DataPermission.search).build(),
				test);

		// get new message acl settings from schema
		messageAcl = SpaceClient.getSchema("message", test).acl();

		// check message schema new acl settings
		assertEquals(1, messageAcl.size());
		assertEquals(Sets.newHashSet(DataPermission.search), messageAcl.get("admin"));

		// get new message acl settings from global settings
		body = SpaceRequest.get("/1/settings/acl")//
				.backend(test).go(200).httpResponse().getBody();
		acl = Json.mapper().readValue(//
				body, DataAclSettings.class);

		// check new global acl settings
		assertEquals(1, acl.size());
		messageAcl = acl.get("message");
		assertEquals(1, messageAcl.size());
		assertEquals(Sets.newHashSet(DataPermission.search), messageAcl.get("admin"));
	}

	@Test
	public void testDataAccessWithRolesAndPermissions() throws Exception {

		// prepare
		SpaceClient.prepareTest();
		Backend test = SpaceClient.resetTestBackend();

		// set schema
		SpaceClient.setSchema(//
				Schema.builder("message").text("text").build(), test);

		// ACL
		Map<String, Map<String, Set<DataPermission>>> acl = Maps.newHashMap();
		Map<String, Set<DataPermission>> messageAcl = Maps.newHashMap();
		messageAcl.put("iron", Sets.newHashSet(DataPermission.read_all));
		messageAcl.put("silver", Sets.newHashSet(DataPermission.read_all, DataPermission.update_all));
		messageAcl.put("gold", Sets.newHashSet(//
				DataPermission.read_all, DataPermission.update_all, DataPermission.create));
		messageAcl.put("platine", Sets.newHashSet(//
				DataPermission.read_all, DataPermission.update_all, DataPermission.create, DataPermission.delete_all));
		acl.put("message", messageAcl);

		SpaceRequest.put("/1/settings/acl").adminAuth(test)//
				.body(Json.mapper().writeValueAsString(acl)).go(200);

		// dave has the platine role
		// he's got all the rights
		User dave = SpaceClient.newCredentials(test, "dave", "hi dave");
		SpaceRequest.put("/1/credentials/dave/roles/platine").adminAuth(test).go(200);
		SpaceRequest.post("/1/data/message?id=dave").userAuth(dave).body("text", "Dave").go(201);
		SpaceRequest.get("/1/data/message/dave").userAuth(dave).go(200);
		SpaceRequest.put("/1/data/message/dave").userAuth(dave).body("text", "Salut Dave").go(200);
		SpaceRequest.delete("/1/data/message/dave").userAuth(dave).go(200);

		// message for users without create permission
		SpaceRequest.post("/1/data/message?id=1").userAuth(dave).body("text", "Hello").go(201);

		// maelle is a simple user
		// she's got no right on the message schema
		User maelle = SpaceClient.newCredentials(test, "maelle", "hi maelle");
		SpaceRequest.post("/1/data/message").userAuth(maelle).body("text", "Maelle").go(403);
		SpaceRequest.get("/1/data/message/1").userAuth(maelle).go(403);
		SpaceRequest.put("/1/data/message/1").userAuth(maelle).body("text", "Salut Maelle").go(403);
		SpaceRequest.delete("/1/data/message/1").userAuth(maelle).go(403);

		// fred has the iron role
		// he's only got the right to read
		User fred = SpaceClient.newCredentials(test, "fred", "hi fred");
		SpaceRequest.put("/1/credentials/fred/roles/iron").adminAuth(test).go(200);
		SpaceRequest.post("/1/data/message").userAuth(fred).body("text", "Fred").go(403);
		SpaceRequest.get("/1/data/message/1").userAuth(fred).go(200);
		SpaceRequest.put("/1/data/message/1").userAuth(fred).body("text", "Salut Fred").go(403);
		SpaceRequest.delete("/1/data/message/1").userAuth(fred).go(403);

		// nath has the silver role
		// she's got the right to read and update
		User nath = SpaceClient.newCredentials(test, "nath", "hi nath");
		SpaceRequest.put("/1/credentials/nath/roles/silver").adminAuth(test).go(200);
		SpaceRequest.post("/1/data/message").userAuth(nath).body("text", "Nath").go(403);
		SpaceRequest.get("/1/data/message/1").userAuth(nath).go(200);
		SpaceRequest.put("/1/data/message/1").userAuth(nath).body("text", "Salut Nath").go(200);
		SpaceRequest.delete("/1/data/message/1").userAuth(nath).go(403);

		// vince has the gold role
		// he's got the right to create, read and update
		User vince = SpaceClient.newCredentials(test, "vince", "hi vince");
		SpaceRequest.put("/1/credentials/vince/roles/gold").adminAuth(test).go(200);
		SpaceRequest.post("/1/data/message?id=vince").userAuth(vince).body("text", "Vince").go(201);
		SpaceRequest.get("/1/data/message/vince").userAuth(vince).go(200);
		SpaceRequest.put("/1/data/message/vince").userAuth(vince).body("text", "Salut Vince").go(200);
		SpaceRequest.delete("/1/data/message/vince").userAuth(vince).go(403);
	}
}
