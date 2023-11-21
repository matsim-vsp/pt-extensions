/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2021 by the members listed in the COPYING,        *
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


package org.matsim.extensions.pt.utils;

import com.google.common.base.Verify;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.utils.gis.shp2matsim.ShpGeometryUtils;

import java.net.URL;
import java.util.*;

/**
 * Tool for tagging TransitStopFacilities e.g. by gtfs mode, line ids serving the stop, location in a shape file etc.
 *
 * @author vsp-gleich
 */
public class TransitStopTagger {

    private static final Logger log = LogManager.getLogger(TransitStopTagger.class);

    public static void attributeTransitStopFacilitiesFromTransitScheduleFile(URL fileWithAttributes, TransitSchedule scheduleToBeAttributed) {
        if (fileWithAttributes == null) {
            log.info("No file to copy stop filter attributes from. If stop filter attributes were set for the intermodal pt router, no stops will be found.");
            return;
        }

        log.info("copying TransitStopFacility attributes from file " + fileWithAttributes);
        Scenario dummyScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        (new TransitScheduleReader(dummyScenario)).readURL(fileWithAttributes);
        TransitSchedule attributesSchedule = dummyScenario.getTransitSchedule();

        // Check that the main scenario's transit schedule and the attributes schedule refer to the same TransitStopFacilities.
        Verify.verify(scheduleToBeAttributed.getFacilities().keySet().equals(attributesSchedule.getFacilities().keySet()));

        // Add / overwrite(!) attributes in main scenario's transit schedule
        for (TransitStopFacility attributedStop : attributesSchedule.getFacilities().values()) {
            TransitStopFacility toBeAttributedStop = scheduleToBeAttributed.getFacilities().get(attributedStop.getId());
            if (attributedStop.getAttributes() != null) {
                for (Map.Entry<String, Object> entry : attributedStop.getAttributes().getAsMap().entrySet()) {
                    toBeAttributedStop.getAttributes().putAttribute(entry.getKey(), entry.getValue());
                }
            }
        }

        log.info("added TransitStopFacility attributes to main scenario's TransitStopFacilities from file " + fileWithAttributes);
    }

    public static void tagTransitStopsInShpFile(TransitSchedule transitSchedule,
                                                      String newAttributeName, String newAttributeValue,
                                                      URL shapeFile,
                                                      String oldFilterAttribute, String oldFilterValue,
                                                      double bufferAroundServiceArea) {
        log.info("Tagging pt stops marked for intermodal access/egress in the service area.");
        List<Geometry> geometries = ShpGeometryUtils.loadGeometries(shapeFile);
        List<Geometry> geometriesWithBuffer = new ArrayList<>();
        for (Geometry geometry : geometries) {
            geometriesWithBuffer.add(geometry.buffer(bufferAroundServiceArea));
        }
        for (TransitStopFacility stop : transitSchedule.getFacilities().values()) {
            if (stop.getAttributes().getAttribute(oldFilterAttribute) != null) {
                if (stop.getAttributes().getAttribute(oldFilterAttribute).equals(oldFilterValue)) {
                    if (ShpGeometryUtils.isCoordInGeometries(stop.getCoord(), geometriesWithBuffer)) {
                        stop.getAttributes().putAttribute(newAttributeName, newAttributeValue);
                    }
                }
            }
        }
    }

    /**
     * TODO: add more gtfs modes, rethink
     */
    public static void tagLineAndModeServingStop(TransitSchedule transitSchedule) {
        for (TransitLine line : transitSchedule.getTransitLines().values()) {
            String gtfsTransitMode = "unknown";

            // identify veh type / mode using gtfs route type (3-digit code, also found at the end of the line id (gtfs: route_id))
            int gtfsTransitType;
            try {
                gtfsTransitType = Integer.parseInt((String) line.getAttributes().getAttribute("gtfs_route_type"));
                switch (gtfsTransitType) {
                    // the vbb gtfs file generally uses the new gtfs route types, but some lines use the old enum in the range 0 to 7
                    // see https://sites.google.com/site/gtfschanges/proposals/route-type
                    // and https://developers.google.com/transit/gtfs/reference/#routestxt
                    // In GTFS-VBB-20181214.zip some RE lines are wrongly attributed as type 700 (bus)!

                    case 0, 900, 901, 902, 903, 904, 905, 906 -> gtfsTransitMode = "Tram, Light Rail, Streetcar";
                    case 1, 400, 401, 402, 403, 404, 405 -> gtfsTransitMode = "Subway, Metro";
                    case 2, 100, 101, 102, 103, 104, 105, 106, 107, 108 -> gtfsTransitMode = "Rail";
                    case 3, 700, 701, 702, 703, 704 -> gtfsTransitMode = "Bus";
                    case 4, 1000, 1200 -> gtfsTransitMode = "Ferry";
                    case 5, 1701 -> gtfsTransitMode = "Cable Car";
                    case 6, 1300 -> gtfsTransitMode = "Gondola, Suspended cable car";
                    case 7, 1400 -> gtfsTransitMode = "Funicular";
                    case 11 -> gtfsTransitMode = "Trolleybus";
                    case 12 -> gtfsTransitMode = "Monorail";
                    case 109 -> // VBB gtfs
                            gtfsTransitMode = "S-Bahn";
                        default -> {
                        log.error("unknown gtfs mode type! Line id was " + line.getId().toString() +
                                "; gtfs route type was " + line.getAttributes().getAttribute("gtfs_route_type"));
                        throw new RuntimeException("unknown transit mode");
                    }
                }
            } catch (NumberFormatException e) {
                log.error("unknown gtfs transit mode or gtfs_route_type not given! Line id was " + line.getId().toString() +
                        "; gtfs route type was " + line.getAttributes().getAttribute("gtfs_route_type"));
            }

            int agencyId = Integer.MIN_VALUE;
            try {
                agencyId = Integer.parseInt((String) line.getAttributes().getAttribute("gtfs_agency_id"));
            } catch (NumberFormatException e) {
                log.error("invalid transit agency! Line id was " + line.getId().toString() +
                        "; gtfs agency was " + line.getAttributes().getAttribute("gtfs_agency_id"));
            }

            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop routeStop : route.getStops()) {
                    Object attrGtfsTransitModes = routeStop.getStopFacility().getAttributes().getAttribute("gtfsTransitModes");
                    Set<String> gtfsTransitModes = attrGtfsTransitModes == null ? new HashSet<>() : (attrGtfsTransitModes instanceof Set ? (Set<String>) attrGtfsTransitModes : new HashSet<>());
                    gtfsTransitModes.add(gtfsTransitMode);
                    routeStop.getStopFacility().getAttributes().putAttribute("gtfsTransitModes", gtfsTransitModes);

                    Object attrGtfsAgencyIds = routeStop.getStopFacility().getAttributes().getAttribute("gtfsAgencyIds");
                    Set<Integer> gtfsAgencyIds = attrGtfsAgencyIds == null ? new HashSet<>() : (attrGtfsAgencyIds instanceof Set ? (Set<Integer>) attrGtfsAgencyIds : new HashSet<>());
                    gtfsAgencyIds.add(agencyId);
                    routeStop.getStopFacility().getAttributes().putAttribute("gtfsAgencyIds", gtfsAgencyIds);

                    Object attrMatsimTransitModes = routeStop.getStopFacility().getAttributes().getAttribute("matsimTransportModes");
                    Set<String> matsimTransitModes = attrMatsimTransitModes == null ? new HashSet<>() : (attrMatsimTransitModes instanceof Set ? (Set<String>) attrMatsimTransitModes : new HashSet<>());
                    matsimTransitModes.add(route.getTransportMode());
                    routeStop.getStopFacility().getAttributes().putAttribute("matsimTransitModes", gtfsTransitModes);

                    Object attrLines = routeStop.getStopFacility().getAttributes().getAttribute("transitLines");
                    Set<Id<TransitLine>> lineIds = attrLines == null ? new HashSet<>() : (attrLines instanceof Set ? (Set<Id<TransitLine>>) attrLines : new HashSet<>());
                    lineIds.add(line.getId());
                    routeStop.getStopFacility().getAttributes().putAttribute("transitLines", lineIds);

                    Object attrLinesString = routeStop.getStopFacility().getAttributes().getAttribute("transitLinesString");
                    String lineIdsString = attrLinesString == null ? "" : (attrLinesString instanceof String ? (String) attrLinesString : "");
                    lineIdsString = lineIdsString.length() == 0 ? line.getId().toString() : ( lineIdsString.contains(line.getId().toString()) ? lineIdsString : lineIdsString + "," + line.getId().toString());
                    routeStop.getStopFacility().getAttributes().putAttribute("transitLinesString", lineIdsString);

                    Object attrLines2Deps = routeStop.getStopFacility().getAttributes().getAttribute("transitLines2Deps");
                    Map<Id<TransitLine>, List<Double>> line2Deps = attrLines2Deps == null ? new HashMap<>() : (attrLines2Deps instanceof Map ? (Map<Id<TransitLine>, List<Double>>) attrLines2Deps : new HashMap<>());
                    route.getDepartures().values().forEach(dep -> line2Deps.computeIfAbsent(line.getId(), k -> new ArrayList<Double>()).add(dep.getDepartureTime() + (routeStop.getDepartureOffset().isDefined() ? routeStop.getDepartureOffset().seconds() : routeStop.getArrivalOffset().seconds())));
                    routeStop.getStopFacility().getAttributes().putAttribute("transitLines2Deps", line2Deps);
                }
            }
        }
    }

    public static void tagStopsServedByLineNameStartingWith(TransitSchedule transitSchedule, String lineNamePrefix, String attributeName, String attributeValue) {
        transitSchedule.getFacilities().values().stream().
                filter(stop -> {
                    Object attr = stop.getAttributes().getAttribute("transitLines");
                    Set<Id<TransitLine>> lineIds = attr == null ? null : (attr instanceof Set ? (Set<Id<TransitLine>>) attr : null);
                    return lineIds != null && lineIds.stream().anyMatch(lineId -> lineId.toString().startsWith(lineNamePrefix));
                }).
                forEach(stop -> stop.getAttributes().putAttribute(attributeName, attributeValue));
    }

    public static void tagStopsServedByMinDeparturesXLineNameStartingWith(TransitSchedule transitSchedule, int minDepartures, String lineNamePrefix, String attributeName, String attributeValue) {
        tagStopsServedWithLinesHavingMinXDepartures(transitSchedule, minDepartures, "transitLinesMin" + minDepartures + "Departures");

        transitSchedule.getFacilities().values().stream().
                filter(stop -> {
                    Object attr = stop.getAttributes().getAttribute("transitLinesMin" + minDepartures + "Departures");
                    String lineIds = attr == null ? "" : (attr instanceof Set ? (String) attr : "");
                    return Arrays.stream(lineIds.split(",")).anyMatch(lineName -> lineName.startsWith(lineNamePrefix));
                }).
                forEach(stop -> stop.getAttributes().putAttribute(attributeName, attributeValue));
    }

    public static void tagStopsServedByGtfsMode(TransitSchedule transitSchedule, String gtfsMode, String attributeName, String attributeValue) {
        transitSchedule.getFacilities().values().stream().
                filter(stop -> {
                    Object attr = stop.getAttributes().getAttribute("gtfsTransitModes");
                    Set<String> gtfsModes = attr == null ? null : (attr instanceof Set ? (Set<String>) attr : null);
                    return gtfsModes != null && gtfsModes.stream().anyMatch(attrGtfsMode -> attrGtfsMode.equals(gtfsMode));
                }).
                forEach(stop -> stop.getAttributes().putAttribute(attributeName, attributeValue));
    }

    public static void deleteAllTransitStopFacilityTagsExcept(TransitSchedule transitSchedule, Set<String> attributeNamesToKeep) {
        transitSchedule.getFacilities().values().
                forEach(stop -> {
                    Set<String> attributeNames = stop.getAttributes().getAsMap().keySet();
                    attributeNames.stream().
                            filter(attribute -> !attributeNamesToKeep.contains(attribute)).
                            forEach(attribute -> stop.getAttributes().removeAttribute(attribute));
                });
    }

    public static void tagStopsServedWithLinesHavingMinXDepartures(TransitSchedule transitSchedule, int minDepartures, String attributeName) {
        transitSchedule.getFacilities().values().stream().
                forEach(stop -> {
                    Object attrLines2Deps = stop.getAttributes().getAttribute("transitLines2Deps");
                    Map<Id<TransitLine>, List<Double>> lines2deps = attrLines2Deps == null ? new HashMap<>() : (attrLines2Deps instanceof Map ? (Map<Id<TransitLine>, List<Double>>) attrLines2Deps : new HashMap<>());
                    for (Map.Entry<Id<TransitLine>, List<Double>> line2deps: lines2deps.entrySet()) {
                        if (line2deps.getValue().size() > minDepartures) {
                            Object attrLinesString = stop.getAttributes().getAttribute(attributeName);
                            String lineIdsString = attrLinesString == null ? "" : (attrLinesString instanceof String ? (String) attrLinesString : "");
                            lineIdsString = lineIdsString.length() == 0 ? line2deps.getKey().toString() : lineIdsString + "," + line2deps.getKey().toString();
                            stop.getAttributes().putAttribute(attributeName, lineIdsString);
                        }
                    }
                });
    }

//    public final static void tagStopsServedWithLinesHavingMaxHeadwayXFromTimeYtoZ(TransitSchedule transitSchedule, double maxHeadway, double startTime, double endTime, String attributeName, String attributeValue) {
//        transitSchedule.getFacilities().values().stream().
//                filter(stop -> {
//                    Object attrLines2Deps = stop.getAttributes().getAttribute("transitLines2Deps");
//                    Map<Id<TransitLine>, List<Double>> line2Deps = attrLines2Deps == null ? new HashMap<>() : (attrLines2Deps instanceof Map ? (Map<Id<TransitLine>, List<Double>>) attrLines2Deps : new HashMap<>());
//                    for (TransitLineline)
//
//
//                        route.getDepartures().values().stream().forEach(dep -> line2Deps.get(line.getId()).add(dep.getDepartureTime() + (routeStop.getDepartureOffset().isDefined() ? routeStop.getDepartureOffset().seconds() : routeStop.getArrivalOffset().seconds())));
//                    routeStop.getStopFacility().getAttributes().putAttribute("transitLines2Deps", line2Deps);
//
//
//
//                    Object attr = stop.getAttributes().getAttribute("gtfsTransitModes");
//                    Set<String> gtfsModes = attr == null ? null : (attr instanceof Set ? (Set<String>) attr : null);
//                    return gtfsModes != null && gtfsModes.stream().anyMatch(attrGtfsMode -> attrGtfsMode.equals(gtfsMode));
//                }).
//                forEach(stop -> stop.getAttributes().putAttribute(attributeName, attributeValue));
//    }

    /*
     * Example run script.
     */
    public static void main(String[] args) {
        String scheduleFile = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v5.5-10pct/input/berlin-v5.5-transit-schedule.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(scheduleFile);
        TransitSchedule transitSchedule = scenario.getTransitSchedule();

        TransitStopTagger.tagLineAndModeServingStop(transitSchedule);

        tagStopsServedWithLinesHavingMinXDepartures(transitSchedule, 5, "transitLinesMin5Departures");
        tagStopsServedWithLinesHavingMinXDepartures(transitSchedule, 50, "transitLinesMin50Departures");

        // tag RE/RB/S/U or Metrobus, Metrotram
        TransitStopTagger.tagStopsServedByLineNameStartingWith(transitSchedule, "M", "Metrobus/Metrotram", "someService");
        tagStopsServedByMinDeparturesXLineNameStartingWith(transitSchedule, 50, "M", "Metrobus/Metrotram", "min50Deps");
        tagStopsServedByMinDeparturesXLineNameStartingWith(transitSchedule, 50, "M", "RE/RB/S/U/M", "true");
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "Rail", "RE/RB/S/U/M", "true");
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "S-Bahn", "RE/RB/S/U/M", "true");
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "Subway, Metro", "RE/RB/S/U/M", "true");
        // tag RE/RB/S/U
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "Rail", "RE/RB/S/U", "true");
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "S-Bahn", "RE/RB/S/U", "true");
        TransitStopTagger.tagStopsServedByGtfsMode(transitSchedule, "Subway, Metro", "RE/RB/S/U", "true");

        // we only want to keep certain attributes, not the whole schedule
//        Collection<TransitLine> transitLinesAllDelete = transitSchedule.getTransitLines().values();
//        Iterator<TransitLine> iter = transitLinesAllDelete.iterator();
//        while (iter.hasNext()) {
//            transitSchedule.removeTransitLine(iter.next());
//        }
//        transitSchedule.deleteAllTransitStopFacilityTagsExcept(transitSchedule, )

        new TransitScheduleWriter(transitSchedule).writeFile("berlin-v5.5-transit-schedule-attributes.xml.gz");
    }
}
