/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package org.matsim.extensions.pt.routing;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scoring.functions.ScoringParameters;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.extensions.pt.PtExtensionsConfigGroup;
import org.matsim.extensions.pt.PtExtensionsConfigGroup.IntermodalAccessEgressModeUtilityRandomization;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorConfigGroup;
import org.matsim.extensions.pt.fare.intermodalTripFareCompensator.IntermodalTripFareCompensatorsConfigGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A default implementation of {@link RaptorIntermodalAccessEgress} returning a new RIntermodalAccessEgress,
 * which contains a list of legs (same as in the input), the associated travel time as well as the disutility.
 *
 * @author vsp-gleich / ikaddoura
 */
public class EnhancedRaptorIntermodalAccessEgress implements RaptorIntermodalAccessEgress {

	public static final String MARGINAL_UTILITY_OF_MONEY_PERSONAL_FACTOR_ATTRIBUTE_NAME = "marginalUtilityOfMoneyPersonalFactor";
	private static final Logger log = LogManager.getLogger(EnhancedRaptorIntermodalAccessEgress.class);
	private final ScoringParametersForPerson parametersForPerson;
	Config config;
	PtExtensionsConfigGroup ptExtensionsCfg;
	MultiModeDrtConfigGroup multiModeDrtConfigGroup;
	IntermodalTripFareCompensatorsConfigGroup interModalTripFareCompensatorsCfg;
	boolean hasNotShownIsUsingMarginalUtilityOfMoneyFromPersonAttributeYet = true;

	// for randomization per person, per mode, per direction (but same random value for one combination of this per routing request)
	Id<Person> lastPersonId = Id.createPersonId("");
	RaptorStopFinder.Direction lastDirection = RaptorStopFinder.Direction.EGRESS;
	Map<String, Double> lastModes2Randomization = new HashMap<>();

	Random random = MatsimRandom.getLocalInstance();

	@Inject
    EnhancedRaptorIntermodalAccessEgress(Config config, ScoringParametersForPerson parametersForPerson) {
		this.config = config;
		this.ptExtensionsCfg = ConfigUtils.addOrGetModule(config, PtExtensionsConfigGroup.class);
		this.multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config, MultiModeDrtConfigGroup.class);
		this.interModalTripFareCompensatorsCfg = ConfigUtils.addOrGetModule(config,
				IntermodalTripFareCompensatorsConfigGroup.class);
		this.parametersForPerson = parametersForPerson;
	}

	@Override
    public RIntermodalAccessEgress calcIntermodalAccessEgress( final List<? extends PlanElement> legs, RaptorParameters params, Person person,
                                                               RaptorStopFinder.Direction direction) {
		// maybe nicer using raptor parameters per person ?
		ScoringParameters scoringParams;
		double marginalUtilityOfMoney;

		scoringParams = this.parametersForPerson.getScoringParameters(person);
		try {
			Object attr = person.getAttributes().getAttribute(MARGINAL_UTILITY_OF_MONEY_PERSONAL_FACTOR_ATTRIBUTE_NAME);
			marginalUtilityOfMoney = attr == null ?
					scoringParams.marginalUtilityOfMoney : scoringParams.marginalUtilityOfMoney * Double.parseDouble(attr.toString());
			if (hasNotShownIsUsingMarginalUtilityOfMoneyFromPersonAttributeYet) {
				log.warn("Using person specific marginal utility of money from person attribute " +
						MARGINAL_UTILITY_OF_MONEY_PERSONAL_FACTOR_ATTRIBUTE_NAME +
						" (multiplied with marginalUtilityOfMoney found in ScoringParameters).");
				hasNotShownIsUsingMarginalUtilityOfMoneyFromPersonAttributeYet = false;
			}
		} catch (Exception e) {
			marginalUtilityOfMoney = scoringParams.marginalUtilityOfMoney;
		}
		
        double utility = 0.0;
        double tTime = 0.0;
        for (PlanElement pe : legs) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
				OptionalTime travelTime = ((Leg) pe).getTravelTime();

                // overrides individual parameters per person; use default scoring parameters
                if (travelTime.isDefined()) {
                    tTime += travelTime.seconds();
					utility += travelTime.seconds() * (scoringParams.modeParams
							.get(mode)
							.marginalUtilityOfTraveling_s + (-1) * scoringParams.marginalUtilityOfPerforming_s);
				}
				Double distance = ((Leg)pe).getRoute().getDistance();
				if (distance != 0.) {
					utility += distance * scoringParams.modeParams.get(mode).marginalUtilityOfDistance_m;
					utility += distance
							* scoringParams.modeParams.get(mode).monetaryDistanceCostRate
							* marginalUtilityOfMoney;
				}
				utility += scoringParams.modeParams.get(mode).constant;

				// account for drt fares
				for (DrtConfigGroup drtConfig : multiModeDrtConfigGroup.getModalElements()) {
					if (drtConfig.getMode().equals(mode)) {
						// skip this leg if the drt mode has no fare
						if (drtConfig.getDrtFareParams().isEmpty()) break;
						DrtFareParams drtFareParams = drtConfig.getDrtFareParams().get();
						double fare = 0.;
						if (distance != 0.) {
							fare += drtFareParams.distanceFare_m * distance;
						}

						if (travelTime.isDefined()) {
							fare += drtFareParams.timeFare_h * travelTime.seconds() / 3600.;

						}

						fare += drtFareParams.baseFare;
						fare = Math.max(fare, drtFareParams.minFarePerTrip);
						utility += -1. * fare * marginalUtilityOfMoney;
					}
                }
                
                // account for intermodal trip fare compensations
                for (IntermodalTripFareCompensatorConfigGroup compensatorCfg : interModalTripFareCompensatorsCfg.getIntermodalTripFareCompensatorConfigGroups()) {
                	if (compensatorCfg.getNonPtModes().contains(mode) && compensatorCfg.getPtModes().contains(TransportMode.pt)) {
                		// the following is a compensation, thus positive!
                		utility += compensatorCfg.getCompensationMoneyPerTrip() * marginalUtilityOfMoney;
						utility += compensatorCfg.getCompensationScorePerTrip();
                	}
                }

                //check whether the same agente was already handled for the same direction (for each trip it should always first handle all access stops and then all egress stops)
                // assumes that the RaptorStopFinder handles by person, then by direction, then by mode for each routing request (what DefaultRaptorStopFinder does)
                // -> same person, same direction should be all in one row without other agents in between (otherwise will not work as expected)
                if(!(lastPersonId.equals(person.getId()) && lastDirection.equals(direction))) {
                    lastModes2Randomization.clear();
                    lastPersonId = person.getId();
                    lastDirection = direction;
                }

                // apply randomization to utility if applicable;
                IntermodalAccessEgressModeUtilityRandomization randomization = ptExtensionsCfg.getIntermodalAccessEgressModeUtilityRandomization(mode);
                if (randomization != null) {
                	double utilityRandomizationSigma = randomization.getAdditiveRandomizationWidth();
					if (utilityRandomizationSigma != 0.0) {
						utility += (random.nextDouble() - 0.5) * utilityRandomizationSigma;
					}
					double utilityRandomizationSigmaFrozenPerDirectionAndMode = randomization.getAdditiveRandomizationWidthFrozenPerDirectionAndMode();
                	if (utilityRandomizationSigmaFrozenPerDirectionAndMode != 0.0) {
                        Double additiveRandomizationFrozenPerDirectionAndMode = lastModes2Randomization.get(mode);
                        if (additiveRandomizationFrozenPerDirectionAndMode == null) {
                            /**
                             * logNormal distribution in {@link org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory}
                             */
//                            double normalization = 1. / Math.exp(utilityRandomizationSigma * utilityRandomizationSigma / 2);
//                            lastModes2Randomization.put(mode, Math.exp( utilityRandomizationSigma * random.nextGaussian() ) * normalization);
//                            Does log normal distribution really make sense for a term we add (instead of multiply)?
//                            Maybe rather use log normal distribution to multiply with the estimated travel time?
//                            (fare and distance seem more predictable, but travel time fluctuates)-gl mar'20
                            additiveRandomizationFrozenPerDirectionAndMode = (random.nextDouble() - 0.5) * utilityRandomizationSigmaFrozenPerDirectionAndMode;
                            lastModes2Randomization.put(mode, additiveRandomizationFrozenPerDirectionAndMode);
                        }
//                        System.err.println(person.getId().toString() + ";" + direction.toString() + ";" + additiveRandomization);
//                        utility *= modeRandom; // analogue beta factor (taste variations)

                        utility += additiveRandomizationFrozenPerDirectionAndMode;
//                        positive utility for a leg is hard to interpret and inh theory should not happen, but it can happen with high intermodal compensations. So do not exclude it.
//                        if (utility > 0) {
//                            utility = 0;
//                        }
                    }
                }
            }
        }
        return new RIntermodalAccessEgress(legs, -utility, tTime, direction);
    }
}
