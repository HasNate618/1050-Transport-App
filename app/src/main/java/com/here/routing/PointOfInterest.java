package com.here.routing;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.here.sdk.core.GeoBox;
import com.here.sdk.core.GeoCoordinates;
import com.here.sdk.core.Metadata;
import com.here.sdk.mapviewlite.MapImage;
import com.here.sdk.mapviewlite.MapImageFactory;
import com.here.sdk.mapviewlite.MapMarker;
import com.here.sdk.mapviewlite.MapMarkerImageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PointOfInterest {
    public String type, title, description;
    public GeoCoordinates coordinates;
    public MapMarker marker;
    public static PointOfInterest lastTouchPoint;

    private static List<PointOfInterest> all = new ArrayList<>();

    public PointOfInterest(String type, String title, String description, GeoCoordinates coordinates) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.coordinates = coordinates;

        int markerImage = 0;
        switch (type) {
            case "origin":
                markerImage = R.drawable.green_dot;
                break;
            case "hazard":
                markerImage = R.drawable.red_dot;
                break;
            case "touchPoint":
                markerImage = R.drawable.green_dot;
                lastTouchPoint = this;
                break;
            case "people":
                markerImage = R.drawable.red_dot;
                break;
        }

        marker = new MapMarker(coordinates);
        marker.addImage(MapImageFactory.fromResource(RoutingExample.context.getResources(), markerImage), new MapMarkerImageStyle());

        Metadata metadata = new Metadata();
        metadata.setString("title", title);
        metadata.setString("description", description);
        marker.setMetadata(metadata);

        all.add(this);
    }

    public void remove() {
        all.remove(this);
        if (Objects.equals(type, "touchPoint")) lastTouchPoint = null;
    }

    @SuppressLint("DefaultLocale")
    public String toString() {
        return String.format("Title: %s, Desc: %s, Lat: %.4f, Long: %.4f", title, description, coordinates.latitude, coordinates.longitude);
    }

    public static List<PointOfInterest> getAll() { return all; }

    public static List<PointOfInterest> getType(String type) {
        List<PointOfInterest> result = new ArrayList<>();
        for (PointOfInterest poi: all) {
            if (Objects.equals(poi.type, type)) result.add(poi);
        }
        return result;
    }

    public static List<PointOfInterest> getTypeExcluding(String type, MapMarker marker) {
        List<PointOfInterest> result = new ArrayList<>();
        for (PointOfInterest poi: all) {
            if (Objects.equals(poi.type, type) && marker!=poi.marker) result.add(poi);
        }
        return result;
    }

    public static List<GeoBox> getGeoBoxes(double radiusMeters, MapMarker marker) {
        List<GeoBox> geoBoxes = new ArrayList<>();
        for (PointOfInterest center : getTypeExcluding("hazard", marker)) {
            // Convert radius in meters to degrees (approximation)
            double deltaLat = radiusMeters / 111320.0; // Approximate meters per degree latitude
            double deltaLon = radiusMeters / (111320.0 * Math.cos(Math.toRadians(center.coordinates.latitude))); // Adjust for longitude

            // Define GeoBox using SW and NE corners
            GeoCoordinates southWest = new GeoCoordinates(center.coordinates.latitude - deltaLat, center.coordinates.longitude - deltaLon);
            GeoCoordinates northEast = new GeoCoordinates(center.coordinates.latitude + deltaLat, center.coordinates.longitude + deltaLon);

            geoBoxes.add(new GeoBox(southWest, northEast));
        }
        return geoBoxes;
    }

    public static List<MapMarker> getMapMarkers() {
        List<MapMarker> markers = new ArrayList<>();
        for (PointOfInterest poi : all) {
            markers.add(poi.marker);
        }
        return markers;
    }

    public static PointOfInterest getFromMarker(MapMarker marker) {
        for (PointOfInterest poi: all) if (poi.marker==marker) return poi;
        return null;
    }
}
