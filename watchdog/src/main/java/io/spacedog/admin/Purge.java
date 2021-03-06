package io.spacedog.admin;

import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.client.SpaceRequest;

public class Purge {

	public String run() {

		try {
			int from = 0;
			int size = 100;
			int total = 0;

			do {
				ObjectNode accounts = SpaceRequest.get("/1/backend")//
						.queryParam("from", String.valueOf(from))//
						.queryParam("size", String.valueOf(size))//
						.superdogAuth()//
						.go(200)//
						.objectNode();

				total = accounts.get("total").asInt();
				from = from + size;

				Iterator<JsonNode> elements = accounts.get("results").elements();
				while (elements.hasNext()) {
					String backendId = elements.next().get("backendId").asText();
					SpaceRequest.delete("/1/log")//
							.superdogAuth(backendId)//
							.go(200);
				}

			} while (from < total);

			return AdminJobs.ok(this);

		} catch (Throwable t) {
			return AdminJobs.error(this, t);
		}
	}

	public static void main(String[] args) {
		new Purge().run();
	}
}
