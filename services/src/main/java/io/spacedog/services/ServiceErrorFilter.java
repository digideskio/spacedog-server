package io.spacedog.services;

import com.fasterxml.jackson.databind.node.ObjectNode;

import io.spacedog.utils.Json;
import io.spacedog.utils.JsonBuilder;
import net.codestory.http.Context;
import net.codestory.http.filters.PayloadSupplier;
import net.codestory.http.payload.Payload;

@SuppressWarnings("serial")
public class ServiceErrorFilter implements SpaceFilter {

	@Override
	public Payload apply(String uri, Context context, PayloadSupplier nextFilter) throws Exception {

		Payload payload = null;

		try {
			payload = nextFilter.get();
		} catch (IllegalStateException e) {
			// Fluent wraps non runtime exceptions into IllegalStateException
			// let's unwrap them
			payload = e.getCause() != null ? Payloads.error(e.getCause())//
					: Payloads.error(e);
		} catch (Throwable t) {
			payload = Payloads.error(t);
		}

		if (payload == null)
			payload = Payloads.error(500, //
					"unexpected null payload for [%s] request to [%s]", context.method(), uri);

		// uri is already checked by SpaceFilter default matches method

		if (payload.isError() //
				&& (payload.rawContentType() == null//
						|| !payload.rawContentType().startsWith("application/json"))) {

			JsonBuilder<ObjectNode> nodeBuilder = Json.objectBuilder()//
					.put("success", false)//
					.put("status", payload.code())//
					.object("error");

			if (payload.code() == 404) {
				nodeBuilder.put("message", //
						String.format("[%s] not a valid path", uri));
				return Payloads.json(nodeBuilder, payload.code());
			} else if (payload.code() == 405) {
				nodeBuilder.put("message", //
						String.format("method [%s] not valid for path [%s]", context.method(), uri));
				return Payloads.json(nodeBuilder, payload.code());
			} else {
				nodeBuilder.put("message", "no details available for this error");
				return Payloads.json(nodeBuilder, payload.code());
			}
		}
		return payload;
	}
}