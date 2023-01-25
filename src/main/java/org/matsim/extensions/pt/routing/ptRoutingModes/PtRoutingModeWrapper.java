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

package org.matsim.extensions.pt.routing.ptRoutingModes;

import com.google.common.collect.ImmutableList;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.RoutingRequest;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup.PersonAttribute2ValuePair;
import org.matsim.extensions.pt.routing.ptRoutingModes.PtIntermodalRoutingModesConfigGroup.PtIntermodalRoutingModeParameterSet;

import java.util.List;

/**
 * 
 * @author vsp-gleich
 *
 */
class PtRoutingModeWrapper implements RoutingModule {
	
	private final RoutingModule ptRouter;
	private final PtIntermodalRoutingModeParameterSet routingModeParams;
	private final ImmutableList<PersonAttribute2ValuePair> personAttribute2ValuePairs;
	
	PtRoutingModeWrapper (PtIntermodalRoutingModeParameterSet routingModeParams, RoutingModule ptRouter) {
		this.routingModeParams = routingModeParams;
		this.personAttribute2ValuePairs = ImmutableList.copyOf(routingModeParams.getPersonAttribute2ValuePairs());
		this.ptRouter = ptRouter;
	}

	@Override
	public List<? extends PlanElement> calcRoute(RoutingRequest request) {
		for (PersonAttribute2ValuePair personAttribute2ValuePair: personAttribute2ValuePairs) {
			request.getPerson().getAttributes().putAttribute(personAttribute2ValuePair.getPersonFilterAttribute(),
					personAttribute2ValuePair.getPersonFilterValue());
		}

		List<? extends PlanElement> route = ptRouter.calcRoute(request);
		
		for (PersonAttribute2ValuePair personAttribute2ValuePair: personAttribute2ValuePairs) {
			request.getPerson().getAttributes().removeAttribute(personAttribute2ValuePair.getPersonFilterAttribute());
		}
		
		return route;
	}

}
