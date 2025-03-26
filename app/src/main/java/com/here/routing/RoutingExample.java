package com.here.routing;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.annotation.LongDef;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;

import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.GeoPolyline;
import com.here.sdk.core.Point2D;
import com.here.sdk.core.errors.InstantiationErrorException;
import com.here.sdk.gestures.TapListener;
import com.here.sdk.mapviewlite.Camera;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapPolyline;
import com.here.sdk.mapviewlite.MapPolylineStyle;
import com.here.sdk.mapviewlite.MapViewLite;
import com.here.sdk.mapviewlite.PickMapItemsCallback;
import com.here.sdk.mapviewlite.PickMapItemsResult;
import com.here.sdk.mapviewlite.PixelFormat;
import com.here.sdk.routing.AvoidanceOptions;
import com.here.sdk.routing.CarOptions;
import com.here.sdk.routing.Maneuver;
import com.here.sdk.routing.ManeuverAction;
import com.here.sdk.routing.Route;
import com.here.sdk.routing.RoutingEngine;
import com.here.sdk.routing.Section;
import com.here.sdk.routing.TrafficOptimizationMode;
import com.here.sdk.routing.Waypoint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RoutingExample {

    private static final String TAG = RoutingExample.class.getName();
    private static final int SUBMIT_REQUEST_CODE = 1;

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    private final MapViewLite mapView;
    private final List<MapPolyline> mapPolylines = new ArrayList<>();
    private final RoutingEngine routingEngine;
    private final GeoCoordinates currentCoords = new GeoCoordinates(42.981485, -81.238093);
    private PointOfInterest destinationPoint;
    private GeoCoordinates touchCoords;

    public RoutingExample(Context context, MapViewLite mapView) {
        RoutingExample.context = context;
        this.mapView = mapView;
        Camera camera = mapView.getCamera();
        camera.setTarget(currentCoords);
        camera.setZoomLevel(14);

        try {
            routingEngine = new RoutingEngine();
        } catch (InstantiationErrorException e) {
            throw new RuntimeException("Initialization of RoutingEngine failed: " + e.error.name());
        }

        new PointOfInterest("origin", "Current location", "London Firehouse 4", currentCoords);
        new PointOfInterest("hazard", "Fire", "100 degrees C", new GeoCoordinates(42.988274, -81.240670));
        new PointOfInterest("hazard", "Fire", "120 degrees C", new GeoCoordinates(42.983192, -81.244332));
        new PointOfInterest("hazard", "Fire", "120 degrees C", new GeoCoordinates(42.9855, -81.2456));
        new PointOfInterest("hazard", "Fire", "120 degrees C", new GeoCoordinates(42.987353, -81.241746));

        drawMarkers();
        setTapGestureHandler();
    }

    public void addRoute(GeoCoordinates start, GeoCoordinates end) {
        Waypoint startWaypoint = new Waypoint(start);
        Waypoint destinationWaypoint = new Waypoint(end);

        List<Waypoint> waypoints =
                new ArrayList<>(Arrays.asList(startWaypoint, destinationWaypoint));

        routingEngine.calculateRoute(
                waypoints,
                getCarOptions(),

                (routingError, routes) -> {
                    // On Route Calculated Callback
                    if (routingError == null) {
                        Route route = routes.get(0);
                        showRouteDetails(route);
                        showRouteOnMap(route);
                    } else {
                        showDialog("Error while calculating a route:", routingError.toString());
                    }
                });
    }

    private void showRouteDetails(Route route) {
        long estimatedTravelTimeInSeconds = route.getDuration().getSeconds();
        int lengthInMeters = route.getLengthInMeters();

        String routeDetails =
                "Time: " + formatTime(estimatedTravelTimeInSeconds) + " seconds "
                + "\nDistance: " + lengthInMeters + " metres";

        showDialog("Route Details", routeDetails);
    }

    private String formatTime(long sec) {
        long minutes = (sec % 3600) / 60;
        sec %= 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, sec);
    }

    private void showRouteOnMap(Route route) {
        // Optionally, remove any previous route.
        clearMap();

        if (route != null) {
            // Show route as polyline.
            GeoPolyline routeGeoPolyline = route.getGeometry();
            MapPolylineStyle mapPolylineStyle = new MapPolylineStyle();
            mapPolylineStyle.setColor(0x002DFFBF, PixelFormat.RGBA_8888);
            mapPolylineStyle.setWidthInPixels(10);
            MapPolyline routeMapPolyline = new MapPolyline(routeGeoPolyline, mapPolylineStyle);
            mapView.getMapScene().addMapPolyline(routeMapPolyline);
            mapPolylines.add(routeMapPolyline);

            // Log maneuver instructions per route section.
            List<Section> sections = route.getSections();
            for (Section section : sections) {
                logManeuverInstructions(section);
            }
        }

        drawMarkers();
    }

    private void drawMarkers() {
        if (PointOfInterest.getAll() == null || PointOfInterest.getAll().isEmpty()) {
            Log.e("ERROR", "No POIs to draw");
            return;
        }

        clearMarkers();

        for (PointOfInterest poi : PointOfInterest.getAll()) {
            mapView.getMapScene().addMapMarker(poi.marker);
        }
    }

    private CarOptions getCarOptions() {
        CarOptions carOptions = new CarOptions();
        carOptions.routeOptions.enableTolls = true;
        // Disabled - Traffic optimization is completely disabled, including long-term road closures. It helps in producing stable routes.
        // Time dependent - Traffic optimization is enabled, the shape of the route will be adjusted according to the traffic situation which depends on departure time and arrival time.
        carOptions.routeOptions.trafficOptimizationMode = TrafficOptimizationMode.DISABLED;

        AvoidanceOptions avoidanceOptions = new AvoidanceOptions();
        avoidanceOptions.avoidBoundingBoxAreas = PointOfInterest.getGeoBoxes(15, destinationPoint.marker);
        carOptions.avoidanceOptions = avoidanceOptions;

        return carOptions;
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
        // Clear markers
        clearMarkers();

        // Clear route
        clearRoutes();
    }

    private void clearMarkers() {
        for (MapMarker mapMarker : PointOfInterest.getMapMarkers()) {
            mapView.getMapScene().removeMapMarker(mapMarker);
        }
        PointOfInterest.getMapMarkers().clear();
    }

    private void clearRoutes() {
        for (MapPolyline mapPolyline : mapPolylines) {
            mapView.getMapScene().removeMapPolyline(mapPolyline);
        }
        mapPolylines.clear();

        destinationPoint = null;
        //mapView.getMapScene().removeMapMarker(PointOfInterest.lastTouchPoint.marker);
    }

    private void setTapGestureHandler() {
        mapView.getGestures().setTapListener(new TapListener() {
            @Override
            public void onTap(Point2D touchPoint) {
                pickMapMarker(touchPoint);
            }
        });
    }

    private void pickMapMarker(final Point2D touchPoint) {
        float radiusInPixel = 25;
        mapView.pickMapItems(touchPoint, radiusInPixel, new PickMapItemsCallback() {
            @Override
            public void onMapItemsPicked(@Nullable PickMapItemsResult pickMapItemsResult) {
                if (pickMapItemsResult == null) {
                    addTouchPoint(mapView.getCamera().viewToGeoCoordinates(touchPoint));
                    return;
                }
                MapMarker topmostMapMarker = pickMapItemsResult.getTopmostMarker();
                if (topmostMapMarker == null) {
                    addTouchPoint(mapView.getCamera().viewToGeoCoordinates(touchPoint));
                    return;
                }
                showMarkerDetails(topmostMapMarker);
            }
        });
    }

    private void addTouchPoint(GeoCoordinates coordinates) {
        new PointOfInterest("touchPoint", "", "", coordinates);
        if (PointOfInterest.lastTouchPoint != null) mapView.getMapScene().removeMapMarker(PointOfInterest.lastTouchPoint.marker);
        touchCoords = coordinates;
        drawMarkers();
        showSubmitDialog();
    }

    public void onSubmitResult(Intent data) {
        String title = data.getStringExtra("title");
        String description = data.getStringExtra("description");
        int typeID = data.getIntExtra("type", 0);

        new PointOfInterest(typeID==0?"hazard":"people", title + " (User Submitted)", description, touchCoords);
        drawMarkers();

        clearRoutes();
        if (destinationPoint != null) addRoute(currentCoords, destinationPoint.coordinates);
    }

    private void showMarkerDetails(MapMarker marker) {
        new AlertDialog.Builder(context)
                .setTitle(marker.getMetadata().getString("title"))
                .setMessage(marker.getMetadata().getString("description"))
                .setNegativeButton("Close", null)
                .setNeutralButton("Mark Resolved", (dialogInterface, i) -> {
                    // Handle "Set Resolved" logic here
                    mapView.getMapScene().removeMapMarker(marker);

                    // Clear route if destination removed
                    if (PointOfInterest.getFromMarker(marker)==destinationPoint) clearRoutes();
                    PointOfInterest.getFromMarker(marker).remove();
                })
                .setPositiveButton("Get Route", (dialogInterface, i) -> {
                    destinationPoint = PointOfInterest.getFromMarker(marker);
                    addRoute(currentCoords, destinationPoint.coordinates);
                })
                .show();
    }

    public void showSubmitDialog() {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Add Point Here?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialogInterface, i) -> {
                    Intent intent = new Intent(context, SubmitActivity.class);
                    ((Activity) context).startActivityForResult(intent, SUBMIT_REQUEST_CODE);
                })
                .setOnDismissListener(dialogInterface -> {
                    mapView.getMapScene().removeMapMarker(PointOfInterest.lastTouchPoint.marker);
                    PointOfInterest.lastTouchPoint.remove();
                })
                .create();

        // Set the dialog to show at the bottom
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.show();
    }

    private void showDialog(String title, String message) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.show();
    }
}
