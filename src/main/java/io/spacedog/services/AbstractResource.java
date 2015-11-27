/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.ShardOperationFailedException;
import org.elasticsearch.index.engine.VersionConflictEngineException;
import org.elasticsearch.index.mapper.MergeMappingException;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.rest.RestStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.spacedog.services.Account.InvalidAccountException;
import io.spacedog.services.SchemaValidator.InvalidSchemaException;
import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

public abstract class AbstractResource {

	public static final String JSON_CONTENT = "application/json;charset=UTF-8";
	public static final String HEADER_OBJECT_ID = "x-spacedog-object-id";
	public static final String BASE_URL = "https://spacedog.io";

	protected Payload checkExistence(String index, String type, String field, String value) {
		try {
			return ElasticHelper.get().search(index, type, field, value).getTotalHits() == 0 ? Payload.notFound()
					: Payload.ok();

		} catch (Throwable throwable) {
			return error(throwable);
		}
	}

	public static JsonNode checkNotNullOrEmpty(JsonNode input, String propertyPath, String type) {
		JsonNode node = Json.get(input, propertyPath);
		if (node == null || Strings.isNullOrEmpty(node.asText()))
			throw new IllegalArgumentException(
					String.format("property [%s] is required in type [%s]", propertyPath, type));
		return node;
	}

	public static void checkNotPresent(JsonNode input, String propertyPath, String type) {
		JsonNode node = Json.get(input, propertyPath);
		if (node != null)
			throw new IllegalArgumentException(
					String.format("property [%s] is forbidden in type [%s]", propertyPath, type));
	}

	public static String checkString(JsonNode input, String propertyPath, boolean required, String in) {
		JsonNode node = Json.get(input, propertyPath);
		if (required && node == null)
			throw new IllegalArgumentException(String.format("property [%s] is mandatory in %s", propertyPath, in));
		if (!node.isTextual())
			throw new IllegalArgumentException(String.format("property [%s] must be textual", propertyPath));
		return node.asText();
	}

	public static String toJsonString(Throwable t) {
		return toJsonNode(t).toString();
	}

	public static JsonNode toJsonNode(Throwable t) {
		JsonBuilder<ObjectNode> builder = Json.startObject()//
				.put("type", t.getClass().getName()) //
				.put("message", t.getMessage()) //
				.startArray("trace");

		for (StackTraceElement element : t.getStackTrace()) {
			builder.add(element.toString());
		}

		builder.end();

		if (t.getCause() != null) {
			builder.putNode("cause", toJsonNode(t.getCause()));
		}

		return builder.build();
	}

	public static Payload success() {
		return new Payload(JSON_CONTENT, "{\"success\":true}", HttpStatus.OK);
	}

	public static Payload saved(boolean created, String uri, String type, String id) {
		return saved(created, uri, type, id, 0);
	}

	public static Payload saved(boolean created, String uri, String type, String id, long version) {
		JsonBuilder<ObjectNode> builder = initSavedBuilder(uri, type, id, version);
		return new Payload(JSON_CONTENT, builder.build().toString(), created ? HttpStatus.CREATED : HttpStatus.OK)
				.withHeader(AbstractResource.HEADER_OBJECT_ID, id);
	}

	protected static JsonBuilder<ObjectNode> initSavedBuilder(String uri, String type, String id, long version) {
		JsonBuilder<ObjectNode> builder = Json.startObject() //
				.put("success", true) //
				.put("id", id) //
				.put("type", type) //
				.put("location", toUrl(BASE_URL, uri, type, id));

		if (version > 0) //
			builder.put("version", version);

		return builder;
	}

	public static Payload toPayload(RestStatus status, ShardOperationFailedException[] failures) {

		if (status.getStatus() == 200)
			return success();

		JsonBuilder<ObjectNode> builder = Json.startObject().put("success", false)//
				.startArray("error");

		for (ShardOperationFailedException failure : failures)
			builder.startObject().put("type", failure.getClass().getName()).put("message", failure.reason())
					.put("shardId", failure.shardId()).end();

		return new Payload(JSON_CONTENT, builder.toString(), status.getStatus());
	}

	public static Payload error(int httpStatus) {
		return error(httpStatus, null);
	}

	public static Payload error(Throwable t) {

		if (t instanceof VersionConflictEngineException) {
			return error(HttpStatus.CONFLICT, t);
		}
		if (t instanceof AuthenticationException) {
			return error(HttpStatus.UNAUTHORIZED, t);
		}
		if (t instanceof InvalidAccountException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof NotFoundException) {
			return error(HttpStatus.NOT_FOUND, t);
		}
		if (t instanceof IndexMissingException) {
			return error(HttpStatus.NOT_FOUND, t);
		}
		if (t instanceof IllegalArgumentException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof NumberFormatException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof InvalidSchemaException) {
			return error(HttpStatus.BAD_REQUEST, t);
		}
		if (t instanceof ExecutionException) {
			if (t.getCause() instanceof MergeMappingException)
				return error(HttpStatus.BAD_REQUEST, t.getCause());
			else
				return error(HttpStatus.INTERNAL_SERVER_ERROR, t);
		}

		return error(HttpStatus.INTERNAL_SERVER_ERROR, t);
	}

	public static Payload error(int httpStatus, Throwable throwable) {
		JsonBuilder<ObjectNode> builder = Json.startObject().put("success", false);
		if (throwable != null)
			builder.putNode("error", toJsonNode(throwable));
		return new Payload(JSON_CONTENT, builder.toString(), httpStatus);
	}

	public static Payload error(int httpStatus, String message, Object... args) {
		return error(httpStatus, new RuntimeException(String.format(message, args)));
	}

	/**
	 * @param parameters
	 *            triples with parameter name, value and message
	 * @return a bad request http payload with a json listing invalid parameters
	 */
	protected static Payload invalidParameters(String... parameters) {
		JsonBuilder<ObjectNode> builder = Json.startObject().put("success", false);
		if (parameters.length > 0 && parameters.length % 3 == 0) {
			builder.startObject("invalidParameters");
			for (int i = 0; i < parameters.length; i += 3)
				builder.startObject(parameters[0])//
						.put("value", parameters[1])//
						.put("message", parameters[2]);
		}
		return new Payload(JSON_CONTENT, builder.toString(), HttpStatus.BAD_REQUEST);
	}

	protected static String toUrl(String baseUrl, String uri, String type, String id) {
		return new StringBuilder(baseUrl).append(uri).append('/').append(type).append('/').append(id).toString();
	}

	protected static String getReferenceType(String reference) {
		return Utils.splitBySlash(reference)[0];
	}

	protected static String getReferenceId(String reference) {
		return Utils.splitBySlash(reference)[1];
	}
}
