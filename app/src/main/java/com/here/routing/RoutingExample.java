package com.here.routing;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.here.sdk.core.GeoBox;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.mapviewlite.Camera;
import com.here.sdk.mapviewlite.MapImage;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapMarkerImageStyle;
import com.here.sdk.mapviewlite.MapPolyline;
import com.here.sdk.mapviewlite.MapPolylineStyle;
import com.here.sdk.mapviewlite.MapViewLite;
import com.here.sdk.mapviewlite.PixelFormat;
import com.here.sdk.routing.AvoidanceOptions;
import com.here.sdk.routing.CalculateRouteCallback;
import com.here.sdk.routing.CarOptions;
import com.here.sdk.routing.Maneuver;
import com.here.sdk.routing.ManeuverAction;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.RoutingError;
import com.here.sdk.routing.Section;
import com.here.sdk.routing.TrafficOptimizationMode;
import com.here.sdk.routing.Waypoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RoutingExample {

    private static final String TAG = RoutingExample.class.getName();

    private Context context;
    private MapViewLite mapView;
    private final List<MapMarker> mapMarkerList = new ArrayList<>();
    private final List<MapPolyline> mapPolylines = new ArrayList<>();
    private RoutingEngine routingEngine;
    private GeoCoordinates currentCoords = new GeoCoordinates(42.981485, -81.238093);
    private GeoCoordinates destinationCoords = new GeoCoordinates(42.9882, -81.2451);
    private List<GeoCoordinates> hazardCoords = Arrays.asList(
            new GeoCoordinates(42.9855, -81.2456)
            , new GeoCoordinates(42.988274, -81.240670)
            , new GeoCoordinates(42.983192, -81.244332)
            , new GeoCoordinates(42.987353, -81.241746)
    );

    public RoutingExample(Context context, MapViewLite mapView) {
        this.context = context;
        this.mapView = mapView;
        Camera camera = mapView.getCamera();
        camera.setTarget(midpointCoords(new GeoCoordinates[]{currentCoords, destinationCoords}));
        camera.setZoomLevel(14);

        try {
            routingEngine = new RoutingEngine();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of RoutingEngine failed: " + e.error.name());
        }
    }

    public GeoCoordinates midpointCoords(GeoCoordinates[] geoCoordinates){
        double latSum = 0, longSum = 0;
        for (GeoCoordinates coordinates: geoCoordinates) {
            latSum += coordinates.latitude;
            longSum += coordinates.longitude;
        }

        return new GeoCoordinates(latSum/geoCoordinates.length, longSum/geoCoordinates.length);
    }

    public void addRoute() {

        Waypoint startWaypoint = new Waypoint(currentCoords);
        Waypoint destinationWaypoint = new Waypoint(destinationCoords);

        List<Waypoint> waypoints =
                new ArrayList<>(Arrays.asList(startWaypoint, destinationWaypoint));

        routingEngine.calculateRoute(
                waypoints,
                getCarOptions(),

                new CalculateRouteCallback() {
                    @Override
                    public void onRouteCalculated(@Nullable RoutingError routingError, @Nullable List<Route> routes) {
                        if (routingError == null) {
                            Route route = routes.get(0);
                            showRouteDetails(route);
                            showRouteOnMap(route);
                        } else {
                            showDialog("Error while calculating a route:", routingError.toString());
                        }
                    }
                });
    }

    private void showRouteDetails(Route route) {
        long estimatedTravelTimeInSeconds = route.getDuration().getSeconds();
        long estimatedTrafficDelayInSeconds = route.getTrafficDelay().getSeconds();
        int lengthInMeters = route.getLengthInMeters();

        String routeDetails =
                "Travel Time: " + formatTime(estimatedTravelTimeInSeconds)
                + ", Traffic delay: " + formatTime(estimatedTrafficDelayInSeconds)
                + ", Length: " + formatLength(lengthInMeters);

        showDialog("Route Details", routeDetails);
    }

    private String formatTime(long sec) {
        long hours = sec / 3600;
        long minutes = (sec % 3600) / 60;

        return String.format(Locale.getDefault(), "%02d:%02d", hours, minutes);
    }

    private String formatLength(int meters) {
        int kilometers = meters / 1000;
        int remainingMeters = meters % 1000;

        return String.format(Locale.getDefault(), "%02d.%02d km", kilometers, remainingMeters);
    }

    private void showRouteOnMap(Route route) {
        // Optionally, remove any previous route.
        clearMap();

        // Show route as polyline.
        GeoPolyline routeGeoPolyline = route.getGeometry();
        MapPolylineStyle mapPolylineStyle = new MapPolylineStyle();
        mapPolylineStyle.setColor(0x00908AA0, PixelFormat.RGBA_8888);
        mapPolylineStyle.setWidthInPixels(10);
        MapPolyline routeMapPolyline = new MapPolyline(routeGeoPolyline, mapPolylineStyle);
        mapView.getMapScene().addMapPolyline(routeMapPolyline);
        mapPolylines.add(routeMapPolyline);

        GeoCoordinates startPoint =
                route.getSections().get(0).getDeparturePlace().mapMatchedCoordinates;
        GeoCoordinates destination =
                route.getSections().get(route.getSections().size() - 1).getArrivalPlace().mapMatchedCoordinates;

        // Draw a circle to indicate starting point and destination.
        addCircleMapMarker(startPoint, R.drawable.green_dot);
        addCircleMapMarker(destination, R.drawable.green_dot);

        addMarkersForGeoCoordinates(hazardCoords);

        // Log maneuver instructions per route section.
        List<Section> sections = route.getSections();
        for (Section section : sections) {
            logManeuverInstructions(section);
        }
    }

    private CarOptions getCarOptions() {
        CarOptions carOptions = new CarOptions();
        carOptions.routeOptions.enableTolls = true;
        // Disabled - Traffic optimization is completely disabled, including long-term road closures. It helps in producing stable routes.
        // Time dependent - Traffic optimization is enabled, the shape of the route will be adjusted according to the traffic situation which depends on departure time and arrival time.
        carOptions.routeOptions.trafficOptimizationMode = TrafficOptimizationMode.TIME_DEPENDENT;

        AvoidanceOptions avoidanceOptions = new AvoidanceOptions();
        avoidanceOptions.avoidBoundingBoxAreas = convertGeoCoordinatesToGeoBoxes(hazardCoords, 20);
        carOptions.avoidanceOptions = avoidanceOptions;

        return carOptions;
    }

    private List<GeoBox> convertGeoCoordinatesToGeoBoxes(List<GeoCoordinates> coordinates, double radiusMeters) {
        List<GeoBox> geoBoxes = new ArrayList<>();
        for (GeoCoordinates center : coordinates) {
            // Convert radius in meters to degrees (approximation)
            double deltaLat = radiusMeters / 111320.0; // Approximate meters per degree latitude
            double deltaLon = radiusMeters / (111320.0 * Math.cos(Math.toRadians(center.latitude))); // Adjust for longitude

            // Define GeoBox using SW and NE corners
            GeoCoordinates southWest = new GeoCoordinates(center.latitude - deltaLat, center.longitude - deltaLon);
            GeoCoordinates northEast = new GeoCoordinates(center.latitude + deltaLat, center.longitude + deltaLon);

            geoBoxes.add(new GeoBox(southWest, northEast));
        }
        return geoBoxes;
    }

    public void addMarkersForGeoCoordinates(List<GeoCoordinates> coordinates) {
        for (GeoCoordinates geoCoordinate : coordinates) {
            addCircleMapMarker(geoCoordinate, R.drawable.red_dot);
        }
    }

    private void logManeuverInstructions(Section section) {
        Log.d(TAG, "Log maneuver instructions per route section:");
        List<Maneuver> maneuverInstructions = section.getManeuvers();
        for (Maneuver maneuverInstruction : maneuverInstructions) {
            ManeuverAction maneuverAction = maneuverInstruction.getAction();
            GeoCoordinates maneuverLocation = maneuverInstruction.getCoordinates();
            String maneuverInfo = maneuverInstruction.getText()
                    + ", Action: " + maneuverAction.name()
                    + ", Location: " + maneuverLocation.toString();
            Log.d(TAG, maneuverInfo);
        }
    }

    public void clearMap() {
        clearWaypointMapMarker();
        clearRoute();
    }

    private void clearWaypointMapMarker() {
        for (MapMarker mapMarker : mapMarkerList) {
            mapView.getMapScene().removeMapMarker(mapMarker);
        }
        mapMarkerList.clear();
    }

    private void clearRoute() {
        for (MapPolyline mapPolyline : mapPolylines) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }
        mapPolylines.clear();
    }

    private void addCircleMapMarker(GeoCoordinates geoCoordinates, int resourceId) {
        MapImage mapImage = MapImageFactory.fromResource(context.getResources(), resourceId);
        MapMarker mapMarker = new MapMarker(geoCoordinates);
        mapMarker.addImage(mapImage, new MapMarkerImageStyle());
        mapView.getMapScene().addMapMarker(mapMarker);
        mapMarkerList.add(mapMarker);
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.show();
    }
}
