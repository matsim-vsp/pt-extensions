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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.events.handler.PersonScoreEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup.CompensationCondition;
import org.matsim.testcases.MatsimTestUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author vsp-gleich
 */
public class IntermodalTripFareCompensatorsTest {

	private static final Logger log = LogManager.getLogger( IntermodalTripFareCompensatorsTest.class ) ;
	
	@RegisterExtension
	public MatsimTestUtils utils = new MatsimTestUtils() ;

    /**
     * Test method for {@link IntermodalTripFareCompensatorPerDay}.
     */
    @Test
    public void testIntermodalTripFareCompensatorPerDay() {

        Fixture fixture = new Fixture();
        Config config = fixture.config;
        Scenario scenario = fixture.scenario;

        IntermodalTripFareCompensatorConfigGroup compensatorConfig = new IntermodalTripFareCompensatorConfigGroup();
        compensatorConfig.setCompensationCondition(CompensationCondition.PtModeUsedAnywhereInTheDay);
        compensatorConfig.setNonPtModesAsString(TransportMode.drt + ",drt2");
        compensatorConfig.setPtModesAsString(TransportMode.pt);
        double compensationMoneyPerTrip = 1.0;
        compensatorConfig.setCompensationMoneyPerTrip(compensationMoneyPerTrip);
        double compensationScorePerTrip = 2.0;
        compensatorConfig.setCompensationScorePerTrip(compensationScorePerTrip);
        double compensationMoneyPerDay = 10.0;
        compensatorConfig.setCompensationMoneyPerDay(compensationMoneyPerDay);
        double compensationScorePerDay = 20.0;
        compensatorConfig.setCompensationScorePerDay(compensationScorePerDay);
        
        IntermodalTripFareCompensatorsConfigGroup compensatorsConfig = new IntermodalTripFareCompensatorsConfigGroup();
        compensatorsConfig.addParameterSet(compensatorConfig);
        
        config.addModule(compensatorsConfig);
        
        config.controller().setOutputDirectory(utils.getOutputDirectory());
        config.controller().setLastIteration(0);
        
        Controler controler = new Controler( scenario );
		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
        
        EventsManager events = controler.getEvents();
        PersonMoneySumAndScoreSumCalculator fareSummer = new PersonMoneySumAndScoreSumCalculator();
        events.addHandler(fareSummer);
        
        controler.run();
        
        Map<Id<Person>, Double> person2Fare = fareSummer.getPerson2Fare();
        Map<Id<Person>, Double> person2Score = fareSummer.getPerson2Score();
        
		Assertions.assertNull(person2Fare.get(fixture.personIdNoPtButDrt), "NoPtButDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdNoPtButDrt), "NoPtButDrt received compensation but should not");
		Assertions.assertNull(person2Fare.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");
		
		Assertions.assertEquals(compensationMoneyPerDay + 1 * compensationMoneyPerTrip,
				person2Fare.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");
        Assertions.assertEquals(compensationScorePerDay + 1 * compensationScorePerTrip,
                person2Score.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");
		
		Assertions.assertEquals(compensationMoneyPerDay + 1 * compensationMoneyPerTrip,
				person2Fare.get(fixture.personIdPt1DrtDifferentTrips), MatsimTestUtils.EPSILON, "Pt1DrtDifferentTrips received wrong compensation");
        Assertions.assertEquals(compensationScorePerDay + 1 * compensationScorePerTrip,
                person2Score.get(fixture.personIdPt1DrtDifferentTrips), MatsimTestUtils.EPSILON, "Pt1DrtDifferentTrips received wrong compensation");
		
		Assertions.assertEquals(compensationMoneyPerDay + 3 * compensationMoneyPerTrip,
				person2Fare.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");
        Assertions.assertEquals(compensationScorePerDay + 3 * compensationScorePerTrip,
                person2Score.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");

    }
    
    @Test
    public void testIntermodalTripFareCompensatorPerTrip() {

        Fixture fixture = new Fixture();
        Config config = fixture.config;
        Scenario scenario = fixture.scenario;

        IntermodalTripFareCompensatorConfigGroup compensatorConfig = new IntermodalTripFareCompensatorConfigGroup();
        compensatorConfig.setCompensationCondition(CompensationCondition.PtModeUsedInSameTrip);
        compensatorConfig.setNonPtModesAsString(TransportMode.drt + ",drt2");
        compensatorConfig.setPtModesAsString(TransportMode.pt);
        double compensationMoneyPerTrip = 1.0;
        compensatorConfig.setCompensationMoneyPerTrip(compensationMoneyPerTrip);
        double compensationScorePerTrip = 2.0;
        compensatorConfig.setCompensationScorePerTrip(compensationScorePerTrip);
        
        IntermodalTripFareCompensatorsConfigGroup compensatorsConfig = new IntermodalTripFareCompensatorsConfigGroup();
        compensatorsConfig.addParameterSet(compensatorConfig);
        
        config.addModule(compensatorsConfig);
        
        config.controller().setOutputDirectory(utils.getOutputDirectory());
        config.controller().setLastIteration(0);
        
        Controler controler = new Controler( scenario );
		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
        
        EventsManager events = controler.getEvents();
        PersonMoneySumAndScoreSumCalculator fareSummer = new PersonMoneySumAndScoreSumCalculator();
        events.addHandler(fareSummer);
        
        controler.run();
        
        Map<Id<Person>, Double> person2Fare = fareSummer.getPerson2Fare();
        Map<Id<Person>, Double> person2Score = fareSummer.getPerson2Score();
        
		Assertions.assertNull(person2Fare.get(fixture.personIdNoPtButDrt), "NoPtButDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdNoPtButDrt), "NoPtButDrt received compensation but should not");
		Assertions.assertNull(person2Fare.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");
		
		Assertions.assertEquals(1 * compensationMoneyPerTrip,
				person2Fare.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");
        Assertions.assertEquals(1 * compensationScorePerTrip,
                person2Score.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");
		
		Assertions.assertNull(person2Fare.get(fixture.personIdPt1DrtDifferentTrips), "Pt1DrtDifferentTrips received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdPt1DrtDifferentTrips), "Pt1DrtDifferentTrips received compensation but should not");
		
		Assertions.assertEquals(3 * compensationMoneyPerTrip,
				person2Fare.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");
        Assertions.assertEquals(3 * compensationScorePerTrip,
                person2Score.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");
    }
    
    @Test
    public void testIntermodalTripFareCompensatorPerDayAndPerTripAndReset() {

        Fixture fixture = new Fixture();
        Config config = fixture.config;
        Scenario scenario = fixture.scenario;
        
        IntermodalTripFareCompensatorsConfigGroup compensatorsConfig = new IntermodalTripFareCompensatorsConfigGroup();
        
        IntermodalTripFareCompensatorConfigGroup compensatorPerDayConfig = new IntermodalTripFareCompensatorConfigGroup();
        compensatorPerDayConfig.setCompensationCondition(CompensationCondition.PtModeUsedAnywhereInTheDay);
        compensatorPerDayConfig.setNonPtModesAsString(TransportMode.drt + ",drt2");
        compensatorPerDayConfig.setPtModesAsString(TransportMode.pt);
        double compensationMoneyPerTripAnywhereInTheDay = 111.0;
        compensatorPerDayConfig.setCompensationMoneyPerTrip(compensationMoneyPerTripAnywhereInTheDay);
        double compensationScorePerTripAnywhereInTheDay = 222.0;
        compensatorPerDayConfig.setCompensationScorePerTrip(compensationScorePerTripAnywhereInTheDay);
        double compensationMoneyPerDayAnywhereInTheDay = 1111.0;
        compensatorPerDayConfig.setCompensationMoneyPerDay(compensationMoneyPerDayAnywhereInTheDay);
        double compensationScorePerDayAnywhereInTheDay = 2222.0;
        compensatorPerDayConfig.setCompensationScorePerDay(compensationScorePerDayAnywhereInTheDay);
        compensatorsConfig.addParameterSet(compensatorPerDayConfig);

        IntermodalTripFareCompensatorConfigGroup compensatorPerTripConfig = new IntermodalTripFareCompensatorConfigGroup();
        compensatorPerTripConfig.setCompensationCondition(CompensationCondition.PtModeUsedInSameTrip);
        compensatorPerTripConfig.setNonPtModesAsString(TransportMode.drt + ",drt2");
        compensatorPerTripConfig.setPtModesAsString(TransportMode.pt);
        double compensationMoneyPerTripSameTrip = 1.0;
        compensatorPerTripConfig.setCompensationMoneyPerTrip(compensationMoneyPerTripSameTrip);
        double compensationScorePerTripSameTrip = 2.0;
        compensatorPerTripConfig.setCompensationScorePerTrip(compensationScorePerTripSameTrip);
        compensatorsConfig.addParameterSet(compensatorPerTripConfig);
        
        config.addModule(compensatorsConfig);
        
        config.controller().setOutputDirectory(utils.getOutputDirectory());
        config.controller().setLastIteration(1); // simulate 2 iterations to check that both compensators reset between iterations
        
        Controler controler = new Controler( scenario );
		controler.addOverridingModule(new IntermodalTripFareCompensatorsModule());
        
        EventsManager events = controler.getEvents();
        PersonMoneySumAndScoreSumCalculator fareSummer = new PersonMoneySumAndScoreSumCalculator();
        events.addHandler(fareSummer);
        
        controler.run();
        
        Map<Id<Person>, Double> person2Fare = fareSummer.getPerson2Fare();
        Map<Id<Person>, Double> person2Score = fareSummer.getPerson2Score();
        
        // The result should be the same no matter whether we run 1 or 2 iterations (replanning switched off). So check after 2nd iteration.
		Assertions.assertNull(person2Fare.get(fixture.personIdNoPtButDrt),
				"NoPtButDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdNoPtButDrt),
                "NoPtButDrt received compensation but should not");
		Assertions.assertNull(person2Fare.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");
        Assertions.assertNull(person2Score.get(fixture.personIdPtNoDrt), "PtNoDrt received compensation but should not");

		Assertions.assertEquals(compensationMoneyPerDayAnywhereInTheDay + 1 * (compensationMoneyPerTripAnywhereInTheDay + compensationMoneyPerTripSameTrip),
				person2Fare.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");
        Assertions.assertEquals(compensationScorePerDayAnywhereInTheDay + 1 * (compensationScorePerTripAnywhereInTheDay + compensationScorePerTripSameTrip),
                person2Score.get(fixture.personIdPt1DrtSameTrip), MatsimTestUtils.EPSILON, "Pt1DrtSameTrip received wrong compensation");

		Assertions.assertEquals(compensationMoneyPerDayAnywhereInTheDay + 1 * compensationMoneyPerTripAnywhereInTheDay,
				person2Fare.get(fixture.personIdPt1DrtDifferentTrips), MatsimTestUtils.EPSILON, "Pt1DrtDifferentTrips received wrong compensation");
        Assertions.assertEquals(compensationScorePerDayAnywhereInTheDay + 1 * compensationScorePerTripAnywhereInTheDay,
                person2Score.get(fixture.personIdPt1DrtDifferentTrips), MatsimTestUtils.EPSILON, "Pt1DrtDifferentTrips received wrong compensation");

		Assertions.assertEquals(compensationMoneyPerDayAnywhereInTheDay + 3 * (compensationMoneyPerTripAnywhereInTheDay + compensationMoneyPerTripSameTrip),
				person2Fare.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");
        Assertions.assertEquals(compensationScorePerDayAnywhereInTheDay + 3 * (compensationScorePerTripAnywhereInTheDay + compensationScorePerTripSameTrip),
                person2Score.get(fixture.personIdPt3DrtIn2IntermodalTrips), MatsimTestUtils.EPSILON, "Pt3DrtIn2IntermodalTrips received wrong compensation");
    }
    
    private static class PersonMoneySumAndScoreSumCalculator implements PersonMoneyEventHandler, PersonScoreEventHandler {
        Map<Id<Person>, Double> person2Fare = new HashMap<>();
        Map<Id<Person>, Double> person2Score = new HashMap<>();
    	
        @Override
        public void handleEvent(PersonMoneyEvent event) {
        	if (!person2Fare.containsKey(event.getPersonId())) {
        		person2Fare.put(event.getPersonId(), event.getAmount());
        	} else {
        		person2Fare.put(event.getPersonId(), person2Fare.get(event.getPersonId()) + event.getAmount());
        	}
        }

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
        	person2Fare.clear();
            person2Score.clear();
        }
        
        private Map<Id<Person>, Double> getPerson2Fare() {
        	return person2Fare;
        }
        private Map<Id<Person>, Double> getPerson2Score() {
            return person2Score;
        }
    }
}
