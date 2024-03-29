/* *********************************************************************** *
 * project: org.matsim.*												   *
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
package org.matsim.extensions.pt.routing;

import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress.RIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import com.google.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.contrib.drt.fare.DrtFareParams;
import org.matsim.contrib.drt.optimizer.insertion.extensive.ExtensiveInsertionSearchParams;
import org.matsim.contrib.drt.routing.DrtRoute;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.controler.*;
import org.matsim.core.controler.corelisteners.ControlerDefaultCoreListenersModule;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.scenario.ScenarioByInstanceModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.ScoringParametersForPerson;
import org.matsim.testcases.MatsimTestUtils;
import playground.vsp.scoring.IncomeDependentUtilityOfMoneyPersonScoringParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * @author vsp-gleich
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EnhancedRaptorIntermodalAccessEgressTest {
	private static final Logger log = LogManager.getLogger( EnhancedRaptorIntermodalAccessEgressTest.class ) ;
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public final void testDrtAccess() {
		List<PlanElement> legs = new ArrayList<>();
		RaptorParameters params = null;

		Config config = ConfigUtils.createConfig();
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population pop = scenario.getPopulation();
		PopulationFactory f = pop.getFactory();
		Person person = f.createPerson(Id.createPersonId("personSubpopulationNull"));

		// daily constants / rates are ignored, but set them anyway (to see whether they are used by error)
		PlanCalcScoreConfigGroup scoreCfg = config.planCalcScore();
		scoreCfg.setMarginalUtilityOfMoney(1.0);
		scoreCfg.setPerforming_utils_hr(0.00011 * 3600.0);
		scoreCfg.setMarginalUtlOfWaitingPt_utils_hr(1d); // completely irrelevant, but avoids NullPointerExceptions
		ModeParams walkParams = scoreCfg.getOrCreateModeParams(TransportMode.walk);
		walkParams.setConstant(-1.2);
		walkParams.setDailyMonetaryConstant(-1.3);
		walkParams.setDailyUtilityConstant(-1.4);
		walkParams.setMarginalUtilityOfDistance(-0.00015);
		walkParams.setMarginalUtilityOfTraveling(-0.00016 * 3600.0);
		walkParams.setMonetaryDistanceRate(-0.00017);
		ModeParams drtParams = scoreCfg.getOrCreateModeParams(TransportMode.drt);
		drtParams.setConstant(-2.1);
		drtParams.setDailyMonetaryConstant(-2.2);
		drtParams.setDailyUtilityConstant(-2.3);
		drtParams.setMarginalUtilityOfDistance(-0.00024);
		drtParams.setMarginalUtilityOfTraveling(-0.00025 * 3600.0);
		drtParams.setMonetaryDistanceRate(-0.00026);

		DrtConfigGroup drtConfigGroup = new DrtConfigGroup();
		drtConfigGroup.mode = TransportMode.drt;
		DrtFareParams drtFareParams = new DrtFareParams();
		drtFareParams.baseFare = 1.0;
		drtFareParams.dailySubscriptionFee = 10.0;
		drtFareParams.minFarePerTrip = 2.0;
		drtFareParams.distanceFare_m = 0.0002;
		drtFareParams.timeFare_h = 0.0003 * 3600;
		drtConfigGroup.addParameterSet(drtFareParams);
		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config,
				MultiModeDrtConfigGroup.class);
		multiModeDrtConfigGroup.addParameterSet(drtConfigGroup);

		// create an injector with the matsim infrastructure:
		com.google.inject.Injector injector = org.matsim.core.controler.Injector.createInjector(config, new AbstractModule() {
			@Override
			public void install() {
				install(new NewControlerModule());
				install(new ControlerDefaultCoreListenersModule());
				install(new ControlerDefaultsModule());
				install(new ScenarioByInstanceModule(scenario));
				install(new AbstractModule() {
					@Override
					public void install() {
						bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
					}
				});
			}
		});
		EnhancedRaptorIntermodalAccessEgress raptorIntermodalAccessEgress = (EnhancedRaptorIntermodalAccessEgress) injector.getInstance(RaptorIntermodalAccessEgress.class);

		Leg walkLeg1 = PopulationUtils.createLeg(TransportMode.walk);
		walkLeg1.setDepartureTime(7 * 3600.0);
		walkLeg1.setTravelTime(100);
		Route walkRoute1 = new GenericRouteImpl(Id.createLinkId("dummy1"), Id.createLinkId("dummy2"));
		walkRoute1.setDistance(200.0);
		walkLeg1.setRoute(walkRoute1);
		legs.add(walkLeg1);

		Leg drtLeg = PopulationUtils.createLeg(TransportMode.drt);
		drtLeg.setDepartureTime(7*3600.0 + 100);
		drtLeg.setTravelTime(600); // current total 700
		Route drtRoute = new DrtRoute(Id.createLinkId("dummy2"), Id.createLinkId("dummy3"));
		drtRoute.setDistance(5000.0);
		drtLeg.setRoute(drtRoute);
		legs.add(drtLeg);

		Leg walkLeg2 = PopulationUtils.createLeg(TransportMode.walk);
		walkLeg2.setDepartureTime(7*3600.0 + 700);
		walkLeg2.setTravelTime(300); // current total 1000
		Route walkRoute2 = new GenericRouteImpl(Id.createLinkId("dummy3"), Id.createLinkId("dummy4"));
		walkRoute2.setDistance(400.0);
		walkLeg2.setRoute(walkRoute2);
		legs.add(walkLeg2);

		RIntermodalAccessEgress result = raptorIntermodalAccessEgress.calcIntermodalAccessEgress(legs, params, person, RaptorStopFinder.Direction.ACCESS );

		//Asserts
		Assert.assertEquals("Total travel time is wrong!", 1000.0, result.travelTime, MatsimTestUtils.EPSILON);

		/*
		 * disutility: -1 * ( ASC + distance + time + monetary distance rate + fare)
		 *
		 * walkLeg1: -1 * (-1.2 -0.00015*200 -(0.00016+0.00011)*100 -0.00017*200 -0 ) = 1.291
		 * drtLeg: -1 * (-2.1 -0.00024*5000 -(0.00025+0.00011)*600 -0.00026*5000 -max(2.0, 1+0.0002*5000+0.0003*600) ) = 6.996
		 * walkLeg2: -1 * (-1.2 -0.00015*400 -(0.00016+0.00011)*300 -0.00017*400 -0 ) = 1.409
		 */
		Assert.assertEquals("Total disutility is wrong!", 9.696, result.disutility, MatsimTestUtils.EPSILON);

		for (int i = 0; i < legs.size(); i++) {
			Assert.assertEquals("Input legs != output legs!", legs.get(i), result.routeParts.get(i));
		}
		Assert.assertEquals("Input legs != output legs!", legs.size(), result.routeParts.size());
	}
	
	@Test
	public final void testWalkAccess() {
		List<PlanElement> legs = new ArrayList<>();
		RaptorParameters params = null;

		Config config = ConfigUtils.createConfig();
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population pop = scenario.getPopulation();
		PopulationFactory f = pop.getFactory();
		Person person = f.createPerson(Id.createPersonId("personSubpopulationNull"));

		// daily constants / rates are ignored, but set them anyway (to see whether they are used by error)
		PlanCalcScoreConfigGroup scoreCfg = config.planCalcScore();
		scoreCfg.setMarginalUtilityOfMoney(1.0);
		scoreCfg.setPerforming_utils_hr(0.00011 * 3600.0);
		scoreCfg.setMarginalUtlOfWaitingPt_utils_hr(1d); // completely irrelevant, but avoids NullPointerExceptions
		ModeParams walkParams = scoreCfg.getOrCreateModeParams(TransportMode.walk);
		walkParams.setConstant(-1.2);
		walkParams.setDailyMonetaryConstant(-1.3);
		walkParams.setDailyUtilityConstant(-1.4);
		walkParams.setMarginalUtilityOfDistance(-0.00015);
		walkParams.setMarginalUtilityOfTraveling(-0.00016 * 3600.0);
		walkParams.setMonetaryDistanceRate(-0.00017);

		// create an injector with the matsim infrastructure:
		com.google.inject.Injector injector = org.matsim.core.controler.Injector.createInjector(config, new AbstractModule() {
			@Override
			public void install() {
				install(new NewControlerModule());
				install(new ControlerDefaultCoreListenersModule());
				install(new ControlerDefaultsModule());
				install(new ScenarioByInstanceModule(scenario));
				install(new AbstractModule() {
					@Override
					public void install() {
						bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
					}
				});
			}
		});
		EnhancedRaptorIntermodalAccessEgress raptorIntermodalAccessEgress = (EnhancedRaptorIntermodalAccessEgress) injector.getInstance(RaptorIntermodalAccessEgress.class);

		Leg walkLeg1 = PopulationUtils.createLeg(TransportMode.walk);
		walkLeg1.setDepartureTime(7*3600.0);
		walkLeg1.setTravelTime(100);
		Route walkRoute1 = new GenericRouteImpl(Id.createLinkId("dummy1"), Id.createLinkId("dummy2"));
		walkRoute1.setDistance(200.0);
		walkLeg1.setRoute(walkRoute1);
		legs.add(walkLeg1);

		RIntermodalAccessEgress result = raptorIntermodalAccessEgress.calcIntermodalAccessEgress(legs, params, person, RaptorStopFinder.Direction.ACCESS );

		//Asserts
		Assert.assertEquals("Total travel time is wrong!", 100.0, result.travelTime, MatsimTestUtils.EPSILON);

		/*
		 * disutility: -1 * ( ASC + distance + time + monetary distance rate + fare)
		 *
		 * walkLeg1: -1 * (-1.2 -0.00015*200 -(0.00016+0.00011)*100 -0.00017*200 -0 ) = 1.291
		 */
		Assert.assertEquals("Total disutility is wrong!", 1.291, result.disutility, MatsimTestUtils.EPSILON);

		for (int i = 0; i < legs.size(); i++) {
			Assert.assertEquals("Input legs != output legs!", legs.get(i), result.routeParts.get(i));
		}
		Assert.assertEquals("Input legs != output legs!", legs.size(), result.routeParts.size());
	}
	
	@Test
	public final void testWalkAccessSubpopulation() {
		List<PlanElement> legs = new ArrayList<>();
		RaptorParameters params = null;
		
		Config config = ConfigUtils.createConfig();
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population pop = scenario.getPopulation();
		PopulationFactory f = pop.getFactory();
		Person person = f.createPerson(Id.createPersonId("personSubpopulationDummy"));
		String subpopulationName = "dummySubpopulation";
		person.getAttributes().putAttribute("subpopulation", subpopulationName);
		pop.addPerson(person);

		Person personSubpopulationNull = f.createPerson(Id.createPersonId("personSubpopulationNull"));
		pop.addPerson(personSubpopulationNull);

		// daily constants / rates are ignored, but set them anyway (to see whether they are used by error)
		PlanCalcScoreConfigGroup.ScoringParameterSet scoreCfg = config.planCalcScore().getOrCreateScoringParameters(subpopulationName);
		scoreCfg.setMarginalUtilityOfMoney(1.0);
		scoreCfg.setMarginalUtlOfWaitingPt_utils_hr(1d); // not sure
		scoreCfg.setPerforming_utils_hr(0.00011 * 3600.0);
		ModeParams walkParams = scoreCfg.getOrCreateModeParams(TransportMode.walk);
		walkParams.setConstant(-1.2);
		walkParams.setDailyMonetaryConstant(-1.3);
		walkParams.setDailyUtilityConstant(-1.4);
		walkParams.setMarginalUtilityOfDistance(-0.00015);
		walkParams.setMarginalUtilityOfTraveling(-0.00016 * 3600.0);
		walkParams.setMonetaryDistanceRate(-0.00017);


		// set other values for subpopulation null to check that they are not used by error
		PlanCalcScoreConfigGroup.ScoringParameterSet scoreCfgNullParams = config.planCalcScore().getOrCreateScoringParameters(null); // is this really necessary
		PlanCalcScoreConfigGroup scoreCfgNull = config.planCalcScore();
		scoreCfgNull.setMarginalUtilityOfMoney(1.0);
		scoreCfgNull.setPerforming_utils_hr(0.0002 * 3600.0);
		scoreCfgNull.setMarginalUtlOfWaitingPt_utils_hr(1d); // not sure
		ModeParams walkParamsNull = scoreCfgNull.getOrCreateModeParams(TransportMode.walk);
		walkParamsNull.setConstant(-100);
		walkParamsNull.setMarginalUtilityOfTraveling(0.0);

		// create an injector with the matsim infrastructure:
		com.google.inject.Injector injector = org.matsim.core.controler.Injector.createInjector(config, new AbstractModule() {
			@Override
			public void install() {
				install(new NewControlerModule());
				install(new ControlerDefaultCoreListenersModule());
				install(new ControlerDefaultsModule());
				install(new ScenarioByInstanceModule(scenario));
				install(new AbstractModule() {
					@Override
					public void install() {
						bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
					}
				});
			}
		});
		EnhancedRaptorIntermodalAccessEgress raptorIntermodalAccessEgress = (EnhancedRaptorIntermodalAccessEgress) injector.getInstance(RaptorIntermodalAccessEgress.class);

		Leg walkLeg1 = PopulationUtils.createLeg(TransportMode.walk);
		walkLeg1.setDepartureTime(7*3600.0);
		walkLeg1.setTravelTime(100);
		Route walkRoute1 = new GenericRouteImpl(Id.createLinkId("dummy1"), Id.createLinkId("dummy2"));
		walkRoute1.setDistance(200.0);
		walkLeg1.setRoute(walkRoute1);
		legs.add(walkLeg1);
		
		// Agent in dummy subpopulation
		RIntermodalAccessEgress result = raptorIntermodalAccessEgress.calcIntermodalAccessEgress(legs, params, person, RaptorStopFinder.Direction.ACCESS );
		
		//Asserts
		Assert.assertEquals("Total travel time is wrong!", 100.0, result.travelTime, MatsimTestUtils.EPSILON);
		
		/* 
		 * disutility: -1 * ( ASC + distance + time + monetary distance rate + fare)
		 * 
		 * walkLeg1: -1 * (-1.2 -0.00015*200 -(0.00016+0.00011)*100 -0.00017*200 -0 ) = 1.291
		 */
		Assert.assertEquals("Total disutility is wrong!", 1.291, result.disutility, MatsimTestUtils.EPSILON);

		for (int i = 0; i < legs.size(); i++) {
			Assert.assertEquals("Input legs != output legs!", legs.get(i), result.routeParts.get(i));
		}
		Assert.assertEquals("Input legs != output legs!", legs.size(), result.routeParts.size());
		
		// Agent in subpopulation null
		RIntermodalAccessEgress resultSubpopulationNull = raptorIntermodalAccessEgress.calcIntermodalAccessEgress(legs, params,
				personSubpopulationNull, RaptorStopFinder.Direction.ACCESS );
		
		//Asserts
		Assert.assertEquals("Total travel time is wrong!", 100.0, resultSubpopulationNull.travelTime, MatsimTestUtils.EPSILON);
		
		/* 
		 * disutility: -1 * ( ASC + distance + time + monetary distance rate + fare)
		 * 
		 * walkLeg1: -1 * (-100 -0*200 -(0.0002)*100 -0.0*200 -0 ) = 100.02
		 */
		Assert.assertEquals("Total disutility is wrong!", 100.02, resultSubpopulationNull.disutility, MatsimTestUtils.EPSILON);

		for (int i = 0; i < legs.size(); i++) {
			Assert.assertEquals("Input legs != output legs!", legs.get(i), resultSubpopulationNull.routeParts.get(i));
		}
		Assert.assertEquals("Input legs != output legs!", legs.size(), resultSubpopulationNull.routeParts.size());
	}

	@Test
	public final void testIncomeDependentUtilityOfMoneyPersonScoringParameters() {
		List<PlanElement> legs = new ArrayList<>();
		RaptorParameters params = null;

		Config config = ConfigUtils.createConfig();
		config.controler().setOutputDirectory(utils.getOutputDirectory());
		config.controler().setLastIteration(0);
		config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population pop = scenario.getPopulation();
		PopulationFactory f = pop.getFactory();
		Person person = f.createPerson(Id.createPersonId("personSubpopulationNull"));
		PersonUtils.setIncome(person, 2000.0);
		pop.addPerson(person);
		Plan plan = f.createPlan();
		Activity homeAct = f.createActivityFromCoord("home", new Coord(1,1));
		plan.addActivity(homeAct);
		person.addPlan(plan);

		Person dummyPerson = f.createPerson(Id.createPersonId("personWithHighIncome"));
		PersonUtils.setIncome(dummyPerson, 6000.0);
		pop.addPerson(dummyPerson);
		Plan dummyPlan = f.createPlan();
		Activity homeAct2 = f.createActivityFromCoord("home", new Coord(1,1));
		dummyPlan.addActivity(homeAct2);
		dummyPerson.addPlan(dummyPlan);

		Node node1 = scenario.getNetwork().getFactory().createNode(Id.createNodeId("1"), new Coord(0,0));
		Node node2 = scenario.getNetwork().getFactory().createNode(Id.createNodeId("2"), new Coord(2,2));
		scenario.getNetwork().addNode(node1);
		scenario.getNetwork().addNode(node2);
		scenario.getNetwork().addLink(scenario.getNetwork().getFactory().createLink(Id.createLinkId("1-2"), node1, node2));
		scenario.getNetwork().addLink(scenario.getNetwork().getFactory().createLink(Id.createLinkId("2-1"), node2, node1));

		// daily constants / rates are ignored, but set them anyway (to see whether they are used by error)
		PlanCalcScoreConfigGroup scoreCfg = config.planCalcScore();
		scoreCfg.setMarginalUtilityOfMoney(1.0);
		scoreCfg.setPerforming_utils_hr(0.00011 * 3600.0);
		scoreCfg.setMarginalUtlOfWaitingPt_utils_hr(1d); // completely irrelevant, but avoids NullPointerExceptions
		ModeParams drtParams = scoreCfg.getOrCreateModeParams(TransportMode.drt);
		drtParams.setConstant(-2.1);
		drtParams.setDailyMonetaryConstant(-2.2);
		drtParams.setDailyUtilityConstant(-2.3);
		drtParams.setMarginalUtilityOfDistance(-0.00024);
		drtParams.setMarginalUtilityOfTraveling(-0.00025 * 3600.0);
		drtParams.setMonetaryDistanceRate(-0.00026);

		DrtConfigGroup drtConfigGroup = new DrtConfigGroup();
		drtConfigGroup.mode = TransportMode.drt;
		DrtFareParams drtFareParams = new DrtFareParams();
		drtFareParams.baseFare = 1.0;
		drtFareParams.dailySubscriptionFee = 10.0;
		drtFareParams.minFarePerTrip = 2.0;
		drtFareParams.distanceFare_m = 0.0002;
		drtFareParams.timeFare_h = 0.0003 * 3600;
		drtConfigGroup.addParameterSet(drtFareParams);
		// Make DrtConfigGroup.checkConsistency happy
		drtConfigGroup.maxWaitTime = 900;
		drtConfigGroup.stopDuration = 60;
		drtConfigGroup.maxTravelTimeAlpha = 1.3;
		drtConfigGroup.maxTravelTimeBeta = 300;
		drtConfigGroup.addDrtInsertionSearchParams(new ExtensiveInsertionSearchParams());

		MultiModeDrtConfigGroup multiModeDrtConfigGroup = ConfigUtils.addOrGetModule(config,
				MultiModeDrtConfigGroup.class);
		multiModeDrtConfigGroup.addParameterSet(drtConfigGroup);

		/*
		 * Creating an injector with the matsim infrastructure and
		 * bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).in(Singleton.class);
		 * did not work.
		 */

		Controler controler = new Controler( scenario );
		controler.addOverridingModule( new AbstractModule() {
			@Override
			public void install() {
				bind(RaptorIntermodalAccessEgress.class).to(EnhancedRaptorIntermodalAccessEgress.class);
				bind(ScoringParametersForPerson.class).to(IncomeDependentUtilityOfMoneyPersonScoringParameters.class).in(Singleton.class);
			}
		});
		controler.run();
		com.google.inject.Injector injector = controler.getInjector();

		EnhancedRaptorIntermodalAccessEgress raptorIntermodalAccessEgress = (EnhancedRaptorIntermodalAccessEgress) injector.getInstance(RaptorIntermodalAccessEgress.class);

		Leg drtLeg = PopulationUtils.createLeg(TransportMode.drt);
		drtLeg.setDepartureTime(7*3600.0 + 100);
		drtLeg.setTravelTime(600); // current total 700
		Route drtRoute = new DrtRoute(Id.createLinkId("dummy2"), Id.createLinkId("dummy3"));
		drtRoute.setDistance(5000.0);
		drtLeg.setRoute(drtRoute);
		legs.add(drtLeg);

		RIntermodalAccessEgress result = raptorIntermodalAccessEgress.calcIntermodalAccessEgress(legs, params, person, RaptorStopFinder.Direction.ACCESS );

		//Asserts
		Assert.assertEquals("Total travel time is wrong!", 600.0, result.travelTime, MatsimTestUtils.EPSILON);

		/*
		 * disutility: -1 * ( ASC + distance + time + monetary distance rate + fare)
		 *
		 * marginal utility of money = ((2000+6000)/2) / 2000 = 4000/2000 = 2.0
		 *
		 * drtLeg: -1 * (-2.1 -0.00024*5000 -(0.00025+0.00011)*600 +2.0*(-0.00026*5000 -max(2.0, 1+0.0002*5000+0.0003*600)) ) = 10.476
		 */
		Assert.assertEquals("Total disutility is wrong!", 10.476, result.disutility, MatsimTestUtils.EPSILON);

		for (int i = 0; i < legs.size(); i++) {
			Assert.assertEquals("Input legs != output legs!", legs.get(i), result.routeParts.get(i));
		}
		Assert.assertEquals("Input legs != output legs!", legs.size(), result.routeParts.size());
	}

}
