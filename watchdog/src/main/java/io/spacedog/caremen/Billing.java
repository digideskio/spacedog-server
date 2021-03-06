package io.spacedog.caremen;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.spacedog.admin.AdminJobs;
import io.spacedog.caremen.FareSettings.VehiculeFareSettings;
import io.spacedog.client.SpaceRequest;
import io.spacedog.client.SpaceRequestConfiguration;
import io.spacedog.client.SpaceTarget;
import io.spacedog.sdk.SpaceData.SearchResults;
import io.spacedog.sdk.SpaceData.TermQuery;
import io.spacedog.sdk.SpaceDog;
import io.spacedog.utils.Check;

public class Billing {

	DateTimeFormatter fullFormatter = DateTimeFormat.fullDateTime().withLocale(Locale.FRANCE);
	DateTimeFormatter shortFormatter = DateTimeFormat.shortDateTime().withLocale(Locale.FRANCE);

	public String charge() {

		try {
			SpaceRequestConfiguration configuration = SpaceRequestConfiguration.get();

			SpaceDog dog = SpaceDog.login(//
					configuration.getProperty("backendId"), //
					configuration.getProperty("cashierUsername"), //
					configuration.getProperty("cashierPassword"));

			TermQuery query = new TermQuery();
			query.type = "course";
			query.terms = Lists.newArrayList("status", "completed");
			SearchResults<Course> courses = dog.data().search(query, Course.class);

			FareSettings fareSettings = dog.settings().get(FareSettings.class);

			for (Course course : courses.objects()) {
				try {
					if (course.fare == null)
						computeFare(dog, course, fareSettings);

					if (course.payment.companyId == null)
						chargeBill(dog, course);

					course.status = "billed";
					course.save();

				} catch (Exception e) {
					String url = configuration.target().url(dog.backendId(), //
							"/1/data/course/" + course.id());
					AdminJobs.error(this, "Error billing course " + url, e);
				}
			}

		} catch (Throwable t) {
			return AdminJobs.error(this, t);
		}

		return "OK";
	}

	void computeFare(SpaceDog dog, Course course, FareSettings fareSettings) {

		Check.notNull(course.requestedVehiculeType, "course.requestedVehiculeType");
		Check.notNull(course.pickupTimestamp, "course.pickupTimestamp");
		Check.notNull(course.dropoffTimestamp, "course.dropoffTimestamp");
		Check.notNull(course.driver, "course.driver");
		Check.notNull(course.driver.driverId, "course.driver.driverId");
		Check.notNull(course.driver.vehicule, "course.driver.vehicule");

		TermQuery query = new TermQuery();
		query.type = "courselog";
		query.size = 1000;
		query.terms = Lists.newArrayList("courseId", course.id(), //
				"driverId", course.driver.driverId, //
				"status", Lists.newArrayList("in-progress", "completed"));
		query.sort = "meta.createdAt";
		query.ascendant = true;

		SearchResults<CourseLog> logs = dog.data().search(query, CourseLog.class);

		Check.isTrue(logs.total() <= 1000, //
				"too many course logs [%s] for course [%s]", logs.total(), course.id());

		List<LatLng> points = Lists.newArrayList();

		for (CourseLog log : logs.objects())
			if (log.where != null)
				points.add(log.where);

		String type = course.requestedVehiculeType;
		VehiculeFareSettings typeFareSettings = fareSettings.getFor(type);
		Check.notNull(typeFareSettings.base, type + ".base");
		Check.notNull(typeFareSettings.km, type + ".km");
		Check.notNull(typeFareSettings.min, type + ".min");
		Check.notNull(typeFareSettings.minimum, type + ".minimum");

		course.distance = (long) SphericalUtil.computeLength(points);
		course.time = course.dropoffTimestamp.getMillis() //
				- course.pickupTimestamp.getMillis();

		course.fare = (typeFareSettings.km * course.distance / 1000) //
				+ (typeFareSettings.min * course.time / 60000) //
				+ typeFareSettings.base;

		course.fare = Double.max(typeFareSettings.minimum, course.fare);
		course.driver.gain = course.fare * fareSettings.driverShare;

		course.save();
	}

	void chargeBill(SpaceDog dog, Course course) {

		Check.notNull(course.payment, "course.payment");
		Check.notNull(course.payment.stripe, "course.payment.stripe");
		Check.notNull(course.payment.stripe.customerId, "course.payment.stripe.customerId");
		Check.notNull(course.payment.stripe.cardId, "course.payment.stripe.cardId");
		Check.notNull(course.to.address, "course.to.address");

		String fullPickupDateTime = fullFormatter.print(course.pickupTimestamp);
		String shortPickupDateTime = shortFormatter.print(course.pickupTimestamp);

		Map<String, Object> params = Maps.newHashMap();
		params.put("amount", (int) (course.fare * 100));
		params.put("currency", "eur");
		params.put("customer", course.payment.stripe.customerId);
		params.put("source", course.payment.stripe.cardId);
		params.put("description", //
				String.format("Course du [%s] à destination de [%s]", //
						fullPickupDateTime, course.to.address));
		params.put("statement_descriptor", //
				String.format("Caremen %s", shortPickupDateTime));

		ObjectNode payment = dog.stripe().charge(params);
		course.payment.stripe.paymentId = payment.get("id").asText();
	}

	public static void main(String[] args) {
		SpaceRequest.configuration().target(SpaceTarget.production);
		System.setProperty("backendId", "caredev");
		System.setProperty("cashierUsername", "cashier");
		System.setProperty("cashierPassword", "hi cashier");
		new Billing().charge();
	}
}
