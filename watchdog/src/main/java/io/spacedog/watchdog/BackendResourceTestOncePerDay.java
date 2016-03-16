/**
 * © David Attias 2015
 */
package io.spacedog.watchdog;

import org.junit.Assert;
import org.junit.Test;

import io.spacedog.client.SpaceDogHelper;
import io.spacedog.watchdog.SpaceSuite.TestOncePerDay;

@TestOncePerDay
public class BackendResourceTestOncePerDay extends Assert {

	@Test
	public void createBackendSendsNotificationToSuperDogs() throws Exception {

		SpaceDogHelper.prepareTest();
		// notification is only sent if forTesting = false
		SpaceDogHelper.resetBackend("test", "test", "hi test", "david@spacedog.io", false);
	}
}