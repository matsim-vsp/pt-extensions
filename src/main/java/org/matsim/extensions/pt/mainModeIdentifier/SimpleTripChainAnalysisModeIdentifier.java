
/* *********************************************************************** *
 * project: org.matsim.*
 * TripChainAnalysisModeIdentifier.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
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

package org.matsim.extensions.pt.mainModeIdentifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.router.AnalysisMainModeIdentifier;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ModeStatsControlerListener takes modes from scoreConfig.getAllModes() and ignores everything else.
 * So keep this in mind before using this class.
 *
 * @author vsp-gleich
 *
 */
public final class SimpleTripChainAnalysisModeIdentifier implements AnalysisMainModeIdentifier {
	private static final Logger log = LogManager.getLogger(SimpleTripChainAnalysisModeIdentifier.class);

	@Override public String identifyMainMode( List<? extends PlanElement> planElements ) {
		return planElements.stream().filter(pe -> pe instanceof Leg).map(pe -> ((Leg) pe).getMode()).collect(Collectors.joining("-"));
	}
}
