/**
 * © David Attias 2015
 */
package io.spacedog.services;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.activation.MimetypesFileTypeMap;

import org.joda.time.DateTime;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import io.spacedog.services.S3Resource.S3Key;
import io.spacedog.utils.JsonBuilder;
import io.spacedog.utils.SpaceHeaders;
import io.spacedog.utils.Uris;
import net.codestory.http.Context;
import net.codestory.http.constants.HttpStatus;
import net.codestory.http.payload.Payload;

public class S3Resource extends Resource {

	private static AmazonS3Client s3 = new AmazonS3Client();
	private static MimetypesFileTypeMap typeMap = new MimetypesFileTypeMap();

	static {
		s3.setRegion(Region.getRegion(Regions.fromName(Start.get().configuration().awsRegion())));
	}

	public Payload doGet(String bucketSuffix, String backendId, String[] path, Context context) {
		return doGet(bucketSuffix, backendId, path, false, context);
	}

	public Payload doGet(String bucketSuffix, String backendId, String[] path, boolean web, Context context) {

		String bucketName = getBucketName(bucketSuffix);
		String s3Key = S3Key.get(backendId).add(path).toString();

		Optional<Payload> optional = getContent(bucketName, s3Key, context);
		if (optional.isPresent())
			return optional.get();

		if (web) {
			optional = getContent(bucketName, String.join(SLASH, s3Key, "index.html"), context);
			if (optional.isPresent())
				return optional.get();

			optional = getContent(bucketName, String.join(SLASH, backendId, "404.html"), context);
			if (optional.isPresent())
				return optional.get().withCode(HttpStatus.NOT_FOUND);

			return Payload.notFound();
		}

		return listFolder(bucketName, backendId, s3Key, context);
	}

	private Payload listFolder(String bucketName, String backendId, String s3Key, Context context) {
		ListObjectsRequest request = new ListObjectsRequest()//
				.withBucketName(bucketName)//
				.withPrefix(s3Key + SLASH)//
				.withMaxKeys(context.query().getInteger("size", 100));

		if (!Strings.isNullOrEmpty(context.get("next")))
			request.setMarker(S3Key.get(backendId).add(context.get("next")).toString());

		ObjectListing objects = s3.listObjects(request);

		// only root path allows empty list
		if (objects.getObjectSummaries().isEmpty() //
				&& !backendId.equals(s3Key))
			return Payloads.error(404);

		JsonBuilder<ObjectNode> response = Payloads.minimalBuilder(200);

		if (objects.isTruncated())
			response.put("next", toSpaceKeyFromS3Key(backendId, objects.getNextMarker()));

		response.array("results");

		for (S3ObjectSummary summary : objects.getObjectSummaries()) {
			response.object()//
					.put("path", toSpaceKeyFromS3Key(backendId, summary.getKey()))//
					.put("size", summary.getSize())//
					.put("lastModified", new DateTime(summary.getLastModified().getTime()).toString())//
					.end();
		}

		return Payloads.json(response);
	}

	private Optional<Payload> getContent(String bucketName, String s3Key, Context context) {
		try {
			S3Object object = s3.getObject(bucketName, s3Key);
			ObjectMetadata metadata = object.getObjectMetadata();

			Payload payload = new Payload(metadata.getContentType(), object.getObjectContent())//
					.withHeader(SpaceHeaders.ETAG, //
							metadata.getETag())//
					.withHeader(SpaceHeaders.SPACEDOG_OWNER, //
							metadata.getUserMetaDataOf("owner"));

			if (context.query().getBoolean("withContentDisposition", false))
				payload = payload.withHeader(SpaceHeaders.CONTENT_DISPOSITION, //
						metadata.getContentDisposition());

			return Optional.of(payload);

		} catch (AmazonS3Exception e) {

			// 404 is OK
			if (e.getStatusCode() == 404)
				return Optional.empty();

			throw e;
		}
	}

	public Payload doDelete(String bucketSuffix, Credentials credentials, String[] path) {

		String bucketName = getBucketName(bucketSuffix);
		String s3Key = S3Key.get(credentials.backendId()).add(path).toString();

		JsonBuilder<ObjectNode> builder = Payloads.minimalBuilder(200).array("deleted");

		// first try to delete this path as key

		try {
			ObjectMetadata metadata = s3.getObjectMetadata(bucketName, s3Key);

			if (credentials.isAtLeastUser()) {

				if (isOwner(credentials, metadata)) {
					s3.deleteObject(bucketName, s3Key);
					return Payloads.success();
				} else
					return Payloads.error(401);

			} else if (credentials.isAtLeastAdmin()) {
				s3.deleteObject(bucketName, s3Key);
				builder.add(Uris.join(path));
			}

		} catch (AmazonS3Exception e) {

			if (e.getStatusCode() != 404)
				throw e;
		}

		// second delete all files with this path as prefix

		if (credentials.isAtLeastAdmin()) {

			String next = null;

			do {

				ListObjectsRequest request = new ListObjectsRequest()//
						.withBucketName(bucketName)//
						.withPrefix(s3Key + SLASH)//
						.withMaxKeys(100);

				if (next != null)
					request.setMarker(next);

				ObjectListing objects = s3.listObjects(request);

				if (!objects.getObjectSummaries().isEmpty()) {
					s3.deleteObjects(new DeleteObjectsRequest(bucketName)//
							.withKeys(objects.getObjectSummaries()//
									.stream()//
									.map(summary -> new KeyVersion(summary.getKey()))//
									.collect(Collectors.toList())));
				}

				for (S3ObjectSummary summary : objects.getObjectSummaries())
					builder.add(toSpaceKeyFromS3Key(credentials.backendId(), summary.getKey()));

				next = objects.getNextMarker();

			} while (next != null);

		}
		return Payloads.json(builder);
	}

	public Payload doUpload(String bucketSuffix, String rootUri, Credentials credentials, String[] path, byte[] bytes,
			Context context) {

		String fileName = path[path.length - 1];
		String bucketName = getBucketName(bucketSuffix);
		String s3Key = S3Key.get(credentials.backendId()).add(path).toString();

		ObjectMetadata metadata = new ObjectMetadata();
		// TODO
		// use the provided content-type if specific first
		// if none derive from file extension
		metadata.setContentType(typeMap.getContentType(fileName));
		metadata.setContentLength(Long.valueOf(context.header("Content-Length")));
		metadata.setContentDisposition(String.format("attachment; filename=\"%s\"", fileName));
		metadata.addUserMetadata("owner", credentials.name());
		metadata.addUserMetadata("owner-type", credentials.level().toString());

		s3.putObject(new PutObjectRequest(bucketName, //
				s3Key, new ByteArrayInputStream(bytes), //
				metadata));

		return Payloads.json(Payloads.minimalBuilder(200)//
				.put("path", Uris.join(path))//
				.put("location", toSpaceLocation(credentials.backendId(), rootUri, path))//
				.put("s3", toS3Location(bucketName, s3Key)));
	}

	//
	// Implementation
	//

	private boolean isOwner(Credentials credentials, ObjectMetadata metadata) {
		return credentials.name().equals(metadata.getUserMetaDataOf("owner")) //
				&& credentials.level().toString().equals(metadata.getUserMetaDataOf("owner-type"));
	}

	private String toSpaceKeyFromS3Key(String backendId, String s3Key) {
		return s3Key.substring(backendId.length());
	}

	private String toSpaceLocation(String backendId, String root, String[] path) {
		return spaceUrl(backendId, root)//
				.append(Uris.join(path)).toString();
	}

	private String toS3Location(String bucketName, String s3Key) {
		return new StringBuilder("https://").append(bucketName)//
				.append(".s3.amazonaws.com/").append(s3Key).toString();
	}

	public static class S3Key {

		boolean slashIsNeeded = false;
		private StringBuilder builder;

		public S3Key() {
			this.builder = new StringBuilder();
		}

		public S3Key add(String... names) {
			if (names != null)
				for (String name : names)
					add(name);

			return this;
		}

		public S3Key add(String name) {
			if (Strings.isNullOrEmpty(name))
				return this;

			if (slashIsNeeded)
				if (name.startsWith(SLASH))
					builder.append(name);
				else
					builder.append(SLASH).append(name);
			else if (name.startsWith(SLASH))
				builder.append(name.substring(1));
			else
				builder.append(name);

			slashIsNeeded = !name.endsWith(SLASH);
			return this;
		}

		@Override
		public String toString() {
			String result = builder.toString();
			if (result.endsWith(SLASH))
				result = result.substring(0, result.length() - 1);
			return result;
		}

		public static S3Key get(String... names) {
			return new S3Key().add(names);
		}
	}
}
