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

package org.matsim.extensions.pt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * 
 * @author ikaddoura
 */

public class PtExtensionsConfigGroup extends ReflectiveConfigGroup {
	public static final String GROUP_NAME = "ptExtensions" ;

	public PtExtensionsConfigGroup() {
		super(GROUP_NAME);
	}
	
	private Map<String, IntermodalAccessEgressModeUtilityRandomization> intermodalAccessEgressMode2utilityRandomization = new HashMap<>();
	
    public void addIntermodalAccessEgressModeUtilityRandomization(IntermodalAccessEgressModeUtilityRandomization paramSet) {
        this.intermodalAccessEgressMode2utilityRandomization.put(paramSet.getAccessEgressMode(), paramSet);
        super.addParameterSet(paramSet);
    }

    public IntermodalAccessEgressModeUtilityRandomization getIntermodalAccessEgressModeUtilityRandomization(String accessEgressMode) {
        return this.intermodalAccessEgressMode2utilityRandomization.get(accessEgressMode);
    }
	
    public Collection<IntermodalAccessEgressModeUtilityRandomization> getIntermodalAccessEgressModeUtilityRandomizations() {
        return this.intermodalAccessEgressMode2utilityRandomization.values();
    }
	
    public static class IntermodalAccessEgressModeUtilityRandomization extends ReflectiveConfigGroup {
	 
        private static final String TYPE = "intermodalAccessEgressModeUtilityRandomization";

        private static final String ACCESS_EGRESS_MODE = "accessEgressMode";
        private static final String ADDITIVE_RANDOMIZATION_WIDTH = "additiveRandomizationWidth";

        private static final String ADDITIVE_RANDOMIZATION_WIDTH_FROZEN_PER_DIRECTION_AND_MODE = "additiveRandomizationWidthFrozenPerDirectionAndMode";

        private String accessEgressMode;
        private double additiveRandomizationWidth = 0;
        private double additiveRandomizationWidthFrozenPerDirectionAndMode = 0;

        public IntermodalAccessEgressModeUtilityRandomization() {
            super(TYPE);
        }
        
        @StringGetter(ACCESS_EGRESS_MODE)
        public String getAccessEgressMode() {
            return accessEgressMode;
        }

        @StringSetter(ACCESS_EGRESS_MODE)
        public void setAccessEgressMode(String accessEgressMode) {
            this.accessEgressMode = accessEgressMode;
        }

        @StringGetter(ADDITIVE_RANDOMIZATION_WIDTH)
        public double getAdditiveRandomizationWidth() {
            return additiveRandomizationWidth;
        }

        @StringSetter(ADDITIVE_RANDOMIZATION_WIDTH)
        public void setAdditiveRandomizationWidth(double additiveRandomizationWidth) {
            this.additiveRandomizationWidth = additiveRandomizationWidth;
        }

        @StringGetter(ADDITIVE_RANDOMIZATION_WIDTH_FROZEN_PER_DIRECTION_AND_MODE)
        public double getAdditiveRandomizationWidthFrozenPerDirectionAndMode() {
            return additiveRandomizationWidthFrozenPerDirectionAndMode;
        }

        @StringSetter(ADDITIVE_RANDOMIZATION_WIDTH_FROZEN_PER_DIRECTION_AND_MODE)
        public void setAdditiveRandomizationWidthFrozenPerDirectionAndMode(double additiveRandomizationWidthFrozenPerDirectionAndMode) {
            this.additiveRandomizationWidthFrozenPerDirectionAndMode = additiveRandomizationWidthFrozenPerDirectionAndMode;
        }
    }
			
}

