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

package org.matsim.extensions.pt.fare.intermodalTripFareCompensator;

import com.google.inject.Inject;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.PersonScoreEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.router.StageActivityTypeIdentifier;

import java.util.HashSet;
import java.util.Set;

/**
 * 
 * This class assumes there can be at most 1 drt leg before a pt leg in a trip and at most 1 drt leg after. Further drt
 * legs will not be compensated! This is for performance reasons (set faster than Map)
 * 
 * @author vsp-gleich
 *
 */
public class IntermodalTripFareCompensatorPerTrip implements PersonDepartureEventHandler, ActivityStartEventHandler {
	@Inject private EventsManager events;
	private Set<Id<Person>> personsOnPtTrip = new HashSet<>();
	private Set<Id<Person>> personsOnDrtTrip = new HashSet<>();
	private double compensationMoney;
	private double compensationScore;
	private Set<String> drtModes;
	private Set<String> ptModes;
	
	// for the module
	IntermodalTripFareCompensatorPerTrip(IntermodalTripFareCompensatorConfigGroup intermodalFareConfigGroup) {
		this.compensationMoney = intermodalFareConfigGroup.getCompensationMoneyPerTrip();
		this.compensationScore = intermodalFareConfigGroup.getCompensationScorePerTrip();
		this.drtModes = intermodalFareConfigGroup.getNonPtModes();
		this.ptModes = intermodalFareConfigGroup.getPtModes();
	}
	
	// for tests
	IntermodalTripFareCompensatorPerTrip(IntermodalTripFareCompensatorConfigGroup intermodalFareConfigGroup, EventsManager events) {
		this(intermodalFareConfigGroup);
		this.events = events;
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if (ptModes.contains(event.getLegMode())) {
			personsOnPtTrip.add(event.getPersonId());
			
 			if (personsOnDrtTrip.contains(event.getPersonId())) {
				// drt before pt case: compensate here when pt leg follows
				compensateMoney(event.getTime(), event.getPersonId(), compensationMoney);
				compensateScore(event.getTime(), event.getPersonId(), compensationScore);
				personsOnDrtTrip.remove(event.getPersonId());
			}
		}
		if (drtModes.contains(event.getLegMode())) {
			if (personsOnPtTrip.contains(event.getPersonId())) {
				// drt after pt case: compensate immediately
				compensateMoney(event.getTime(), event.getPersonId(), compensationMoney);
				compensateScore(event.getTime(), event.getPersonId(), compensationScore);
			} else {
				// drt before pt case: compensate later _if_ pt leg follows
				personsOnDrtTrip.add(event.getPersonId());
			}
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (!StageActivityTypeIdentifier.isStageActivity(event.getActType())) {
			// reset after trip has finished
			personsOnPtTrip.remove(event.getPersonId());
			personsOnDrtTrip.remove(event.getPersonId());
		}
		
	}
	
	private void compensateMoney(double time, Id<Person> agentId, double amount) {
		events.processEvent(new PersonMoneyEvent(time, agentId, amount, "intermodalTripFareCompensation", "", ""));
	}

	private void compensateScore(double time, Id<Person> agentId, double amount) {
		events.processEvent(new PersonScoreEvent(time, agentId, amount, "intermodalTripFareCompensation"));
	}

    @Override
    public void reset(int iteration) {
		// reset for stuck agents after mobsim
    	personsOnPtTrip.clear();
    	personsOnDrtTrip.clear();
    }
	
}
