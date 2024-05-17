/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

/**
 *
 */
package org.matsim.extensions.pt.fare.intermodalTripFareCompensator;

import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.events.handler.PersonScoreEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.ParallelEventsManager;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vsp-gleich
 */
public class IntermodalTripFareCompensatorPerTripTest {

    /**
     * Test method for {@link IntermodalTripFareCompensatorPerTrip}.
     */
    @Test
    public void testIntermodalTripFareCompensatorPerTrip() {

        Config config = ConfigUtils.createConfig();
        
        IntermodalTripFareCompensatorConfigGroup compensatorConfig = new IntermodalTripFareCompensatorConfigGroup();
        compensatorConfig.setNonPtModesAsString(TransportMode.drt + ",drt2");
        compensatorConfig.setPtModesAsString(TransportMode.pt);
        double compensationMoneyPerTrip = 1.0;
        double compensationScorePerTrip = 2.0;
        compensatorConfig.setCompensationMoneyPerTrip(compensationMoneyPerTrip);
        compensatorConfig.setCompensationScorePerTrip(compensationScorePerTrip);
        
        config.addModule(compensatorConfig);

        ParallelEventsManager events = new ParallelEventsManager(false);
        IntermodalTripFareCompensatorPerTrip tfh = new IntermodalTripFareCompensatorPerTrip(compensatorConfig, events);
        events.addHandler(tfh);
        
        Map<Id<Person>, Double> person2Fare = new HashMap<>();
        Map<Id<Person>, Double> person2Score = new HashMap<>();
        events.addHandler(new PersonMoneyEventHandler() {
            @Override
            public void handleEvent(PersonMoneyEvent event) {
            	if (!person2Fare.containsKey(event.getPersonId())) {
            		person2Fare.put(event.getPersonId(), event.getAmount());
            	} else {
            		person2Fare.put(event.getPersonId(), person2Fare.get(event.getPersonId()) + event.getAmount());
            	}
            }

            @Override
            public void reset(int iteration) {
            }
        });
        events.addHandler(new PersonScoreEventHandler() {
            @Override
            public void handleEvent(PersonScoreEvent event) {
                if (!person2Score.containsKey(event.getPersonId())) {
                    person2Score.put(event.getPersonId(), event.getAmount());
                } else {
                    person2Score.put(event.getPersonId(), person2Score.get(event.getPersonId()) + event.getAmount());
                }
            }

            @Override
            public void reset(int iteration) {
            }
        });
        events.initProcessing();
        Id<Person> personId1 = Id.createPersonId("p1");

        String routingMode = "routingModeShouldNotMatter";
        Coord dummyCoord = CoordUtils.createCoord(0.0, 0.0, 0.0);

        // test trip with drt mode but not intermodal
        events.processEvent(new PersonDepartureEvent(0.0, personId1, Id.createLinkId("12"), TransportMode.drt, routingMode));
        events.processEvent(new ActivityStartEvent(1.0, personId1, Id.createLinkId("23"), Id.create("dummy", ActivityFacility.class), "work", dummyCoord));
        events.flush();
        Assertions.assertNull(person2Fare.get(personId1), "Compensation money should be 0, but is not!");
        Assertions.assertNull(person2Score.get(personId1), "Compensation should be 0, but is not!");
        
        // test intermodal trip without drt mode (only unrelated other mode)
        events.processEvent(new PersonDepartureEvent(2.0, personId1, Id.createLinkId("23"), TransportMode.car, routingMode));
        events.processEvent(new PersonDepartureEvent(3.0, personId1, Id.createLinkId("34"), TransportMode.pt, routingMode));
        
        // there should be no compensation so far
        events.flush();
        Assertions.assertNull(person2Fare.get(personId1), "Compensation money should be 0, but is not!");
        Assertions.assertNull(person2Score.get(personId1), "Compensation score should be 0, but is not!");

        // test drt after pt leg
        events.processEvent(new PersonDepartureEvent(4.0, personId1, Id.createLinkId("45"), TransportMode.drt, routingMode));
        
        // compensation paid once
        events.flush();
        Assertions.assertEquals(1 * compensationMoneyPerTrip, person2Fare.get(personId1), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation money should be paid once, but is not");
        Assertions.assertEquals(1 * compensationScorePerTrip, person2Score.get(personId1), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation score should be paid once, but is not");

        // some distraction, nothing should change
        events.processEvent(new PersonDepartureEvent(4.0, personId1, Id.createLinkId("45"), TransportMode.pt, routingMode));
		
	    // compensation paid once
        events.flush();
        Assertions.assertEquals(1 * compensationMoneyPerTrip, person2Fare.get(personId1), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation money should be paid once, but is not");
        Assertions.assertEquals(1 * compensationScorePerTrip, person2Score.get(personId1), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation score should be paid once, but is not");

		
		// end trip
        events.processEvent(new ActivityStartEvent(5.0, personId1, Id.createLinkId("23"), Id.create("dummy", ActivityFacility.class), "blub", dummyCoord));
        
        // test drt2 before pt with interaction activity in between
        events.processEvent(new PersonDepartureEvent(6.0, personId1, Id.createLinkId("45"), "drt2", routingMode));
        events.processEvent(new ActivityStartEvent(7.0, personId1, Id.createLinkId("56"), Id.create("dummy", ActivityFacility.class), "drt interaction", dummyCoord));
        events.processEvent(new PersonDepartureEvent(8.0, personId1, Id.createLinkId("56"), TransportMode.pt, routingMode));
        
        // compensation paid second time (second trip)
        events.flush();
        Assertions.assertEquals(2 * compensationMoneyPerTrip, person2Fare.get(personId1), MatsimTestUtils.EPSILON, "After a drt2 and a pt leg compensation money should be paid a 2nd time, but is not");
        Assertions.assertEquals(2 * compensationScorePerTrip, person2Score.get(personId1), MatsimTestUtils.EPSILON, "After a drt2 and a pt leg compensation score should be paid a 2nd time, but is not");

        // some distraction, nothing should change
        events.processEvent(new PersonDepartureEvent(4.0, personId1, Id.createLinkId("45"), TransportMode.pt, routingMode));
        events.flush();
        Assertions.assertEquals(2 * compensationMoneyPerTrip, person2Fare.get(personId1), MatsimTestUtils.EPSILON, "After a drt2 and a pt leg compensation money should be paid a 2nd time, but is not");
        Assertions.assertEquals(2 * compensationScorePerTrip, person2Score.get(personId1), MatsimTestUtils.EPSILON, "After a drt2 and a pt leg compensation score should be paid a 2nd time, but is not");

        events.processEvent(new PersonDepartureEvent(4.0, personId1, Id.createLinkId("67"), TransportMode.drt, routingMode));
        
        // compensation paid third time (second trip)
        events.flush();
        Assertions.assertEquals(3 * compensationMoneyPerTrip, person2Fare.get(personId1), MatsimTestUtils.EPSILON, "After another drt leg compensation money should be paid a 3nd time, but is not");
        Assertions.assertEquals(3 * compensationScorePerTrip, person2Score.get(personId1), MatsimTestUtils.EPSILON, "After another drt leg compensation score should be paid a 3nd time, but is not");

        Id<Person> personId2 = Id.createPersonId("p2");
        // test drt before pt with interaction activity in between at other agent who did not use pt before
        events.processEvent(new PersonDepartureEvent(6.0, personId2, Id.createLinkId("45"), "drt", routingMode));
        events.processEvent(new ActivityStartEvent(7.0, personId2, Id.createLinkId("56"), Id.create("dummy", ActivityFacility.class), "drt interaction", dummyCoord));
        events.processEvent(new PersonDepartureEvent(8.0, personId2, Id.createLinkId("56"), TransportMode.pt, routingMode));
        events.flush();
        Assertions.assertEquals(1 * compensationMoneyPerTrip, person2Fare.get(personId2), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation money should be paid once, but is not");
        Assertions.assertEquals(1 * compensationScorePerTrip, person2Score.get(personId2), MatsimTestUtils.EPSILON, "After a pt and a drt leg compensation score should be paid once, but is not");

    }


}
