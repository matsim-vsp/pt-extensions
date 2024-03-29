/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package org.matsim.extensions.pt.replanning.singleTripStrategies;

import javax.inject.Inject;
import javax.inject.Provider;

import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.groups.ChangeModeConfigGroup;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl.Builder;
import org.matsim.core.replanning.selectors.RandomPlanSelector;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

public class ChangeSingleTripModeAndRoute implements Provider<PlanStrategy> {

	@Inject private GlobalConfigGroup globalConfigGroup;
	@Inject private ChangeModeConfigGroup changeModeConfigGroup;
	@Inject private ActivityFacilities facilities;
	@Inject private Provider<TripRouter> tripRouterProvider;
	@Inject private TimeInterpretation timeInterpretation;

	@Override
	public PlanStrategy get() {
		Builder builder = new Builder(new RandomPlanSelector<Plan,Person>()) ;
		builder.addStrategyModule(new ChangeSingleTripModeAndRouteModule(facilities, tripRouterProvider, globalConfigGroup, changeModeConfigGroup, timeInterpretation));
		return builder.build() ;
	}

}
