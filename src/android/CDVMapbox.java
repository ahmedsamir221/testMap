package com.dagatsoin.plugins.mapbox;

import android.app.Activity;
import android.os.Handler;
import android.content.res.Resources;
import android.graphics.PointF;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.cocoahero.android.geojson.GeoJSON;
import com.cocoahero.android.geojson.GeoJSONObject;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.style.expressions.Expression;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class CDVMapbox extends CordovaPlugin implements ViewTreeObserver.OnScrollChangedListener {

    FrameLayout mapsGroup;

    private static final String ADD_IMAGE = "ADD_IMAGE";
    private static final String ADD_LAYER = "ADD_LAYER";
    private static final String ADD_MAP_CLICK_CALLBACK = "ADD_MAP_CLICK_CALLBACK";
    private static final String ADD_SOURCE = "ADD_SOURCE";
    private static final String ADD_ON_WILL_START_LOADING_MAP_LISTENER = "ADD_ON_WILL_START_LOADING_MAP_LISTENER";
    private static final String ADD_ON_WILL_START_RENDERING_MAP_LISTENER = "ADD_ON_WILL_START_RENDERING_MAP_LISTENER";
    private static final String ADD_ON_CAMERA_WILL_CHANGE_LISTENER = "ADD_ON_CAMERA_WILL_CHANGE_LISTENER";
    private static final String ADD_ON_CAMERA_DID_CHANGE_LISTENER = "ADD_ON_CAMERA_DID_CHANGE_LISTENER";
    private static final String ADD_ON_DID_FINISH_LOADING_STYLE_LISTENER = "ADD_ON_DID_FINISH_LOADING_STYLE_LISTENER";
    private static final String ADD_ON_SOURCE_CHANGED_LISTENER = "ADD_ON_SOURCE_CHANGED_LISTENER";
    private static final String ADD_ON_WILL_START_RENDERING_FRAME_LISTENER = "ADD_ON_WILL_START_RENDERING_FRAME_LISTENER";
    private static final String ADD_ON_DID_FINISH_RENDERING_FRAME_LISTENER = "ADD_ON_DID_FINISH_RENDERING_FRAME_LISTENER";
    private static final String ADD_ON_DID_FINISH_LOADING_MAP_LISTENER = "ADD_ON_DID_FINISH_LOADING_MAP_LISTENER";
    private static final String ADD_ON_DID_FINISH_RENDERING_MAP_LISTENER = "ADD_ON_DID_FINISH_RENDERING_MAP_LISTENER";
    private static final String CONVERT_COORDINATES = "CONVERT_COORDINATES";
    private static final String CONVERT_POINT = "CONVERT_POINT";
    private static final String DELETE_OFFLINE_REGION = "DELETE_OFFLINE_REGION";
    private static final String DESTROY = "DESTROY";
    private static final String DOWNLOAD_CURRENT_MAP = "DOWNLOAD_CURRENT_MAP";
    private static final String FLY_TO = "FLY_TO";
    private static final String GET_BOUNDS = "GET_BOUNDS";
    private static final String GET_CAMERA_POSITION = "GET_CAMERA_POSITION";
    private static final String GET_CENTER = "GET_CENTER";
    private static final String GET_OFFLINE_REGIONS_LIST = "GET_OFFLINE_REGIONS_LIST";
    private static final String GET_PITCH = "GET_PITCH";
    private static final String GET_ZOOM = "GET_ZOOM";
    private static final String HIDE = "HIDE";
    private static final String PAUSE_DOWNLOAD = "PAUSE_DOWNLOAD";
    private static final String REMOVE_IMAGE = "REMOVE_IMAGE";
    private static final String REMOVE_SOURCE = "REMOVE_SOURCE";
    private static final String REMOVE_LAYER = "REMOVE_LAYER";
    private static final String RESIZE = "RESIZE";
    private static final String SCROLL_MAP = "SCROLL_MAP";
    private static final String SET_CENTER = "SET_CENTER";
    private static final String SET_CLICKABLE = "SET_CLICKABLE";
    private static final String SET_CONTAINER = "SET_CONTAINER";
    private static final String SET_DEBUG = "SET_DEBUG";
    private static final String SET_GEO_JSON = "SET_GEO_JSON";
    private static final String SET_LAYOUT_PROPERTY = "SET_LAYOUT_PROPERTY";
    private static final String SET_PITCH = "SET_PITCH";
    private static final String SET_ZOOM = "SET_ZOOM";
    private static final String SHOW = "SHOW";
    private static final String ZOOM_TO = "ZOOM_TO";

    private static final String MAPBOX_ACCESSTOKEN_RESOURCE_KEY = "mapbox_accesstoken";
    private CordovaWebView _webView;
    private Activity activity;

    PluginLayout pluginLayout;

    /**
     * Handler listening to scroll changes.
     * Important! Both pluginLayout and maps have to be updated.
     */
    @Override
    public void onScrollChanged() {
        if (pluginLayout == null) {
            return;
        }
        int scrollX = _webView.getView().getScrollX();
        int scrollY = _webView.getView().getScrollY();

        pluginLayout.scrollTo(scrollX, scrollY);

        for (int i = 0; i < MapsManager.getCount(); i++) {
            MapsManager.getMap(i).onScroll(scrollX, scrollY);
        }
    }

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        super.initialize(cordova, webView);

        _webView = webView;
        activity = cordova.getActivity();
        _webView.getView().getViewTreeObserver().addOnScrollChangedListener(CDVMapbox.this);

        /*
         * Init MapsManager. It handles multiple maps.
         */
        MapsManager.init(this, activity);

        /*
         * Init the plugin layer responsible to capture touch events.
         * It permits to have Dom Elements on top of the map.
         * If a touch event occurs in one of the embed rectangles and outside of a inner html element,
         * the plugin layer considers that is a map action (drag, pan, etc.).
         * If not, the user surely want to access the UIWebView.
         */
        pluginLayout = new PluginLayout(_webView.getView(), activity);


        // Create the maps container.
        mapsGroup = new FrameLayout(webView.getContext());
        mapsGroup.setLayoutParams(
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                )
        );

        // make webview transparent to see the map through
        //_root.setBackgroundColor(Color.WHITE);
        //webView.getView().setBackgroundColor(Color.TRANSPARENT);

        try {
            int mapboxAccesstokenResourceId = cordova.getActivity().getResources().getIdentifier(MAPBOX_ACCESSTOKEN_RESOURCE_KEY, "string", cordova.getActivity().getPackageName());
            final String _accessToken = cordova.getActivity().getString(mapboxAccesstokenResourceId);

            Handler mainHandler = new Handler(activity.getMainLooper());

            mainHandler.post(() -> Mapbox.getInstance(activity, _accessToken));
        } catch (Resources.NotFoundException e) {
            // we'll deal with this when the _accessToken property is read, but for now let's dump the error:
            e.printStackTrace();
        }
    }

    public boolean execute(final String action, final CordovaArgs args, final CallbackContext callbackContext) {

        try {
            if (args.isNull(0)) {
                callbackContext.error(action + " needs a map id");
                return false;
            }

            final int id = args.getInt(0);
            final Map map = MapsManager.getMap(id);

            if (action.equals(SHOW)) {
                if (map != null) {
                    if (map.getMapCtrl().getMapView().getVisibility() == View.GONE) {
                        activity.runOnUiThread(() -> {
                            map.getMapCtrl().getMapView().setVisibility(View.VISIBLE);
                            callbackContext.success();
                        });
                        return true;
                    } else {
                        callbackContext.error("Map is already displayed");
                        return false;
                    }
                } else {
                    activity.runOnUiThread(() -> {
                        final Map aMap = MapsManager.createMap(args, id, callbackContext);
                        /* If it is the first map, we set the general layout.
                         * Arrange the layers. The final order is:
                         * - root (Application View)
                         *   - pluginLayout
                         *     - frontLayout
                         *       - webView
                         *     - scrollView
                         *       - scrollFrameLayout
                         *         - mapsGroup
                         *         - background
                         */

                        if (MapsManager.getCount() == 1) {
                            pluginLayout.attachMapsGroup(mapsGroup);
                        }
                        aMap.setContainer(args, callbackContext);
                        mapsGroup.addView(aMap.getViewGroup());
                        aMap.getMapCtrl().mapReady = callbackContext::success;
                    });
                    return true;
                }
            }

            // need a map for all following actions
            if (map == null || !map.getMapCtrl().isReady) {
                callbackContext.error(action + " map is not ready");
                callbackContext.success();
                return false;
            }

            final MapController mapCtrl = map.getMapCtrl();

            switch (action) {
                case HIDE:
                    activity.runOnUiThread(() -> {
                        map.getMapCtrl().getMapView().setVisibility(View.GONE);
                        map.getMapCtrl().deselectFeature();
                        if (mapCtrl.isDownloading()) mapCtrl.pauseDownload();
                        callbackContext.success();
                    });
                    break;
                case DESTROY:
                    activity.runOnUiThread(() -> {
                        if (mapCtrl.isDownloading()) mapCtrl.pauseDownload();
                        mapsGroup.removeView(map.getViewGroup());
                        MapsManager.removeMap(id);
                        if (MapsManager.getCount() == 0) {
                            pluginLayout.detachMapsGroup();
                        }
                    });
                    break;
                case RESIZE:
                    activity.runOnUiThread(() -> map.setContainer(args, callbackContext));

                    break;
                case GET_ZOOM:
                    activity.runOnUiThread(() -> {
                        try {
                            callbackContext.success(new JSONObject("{\"zoom\":" + mapCtrl.getZoom() + '}'));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    });

                    break;
                case SET_ZOOM:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1)) {
                                throw new JSONException(action + "needs a zoom level");
                            }
                            double zoom = args.getDouble(1);
                            mapCtrl.setZoom(zoom);
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error(e.getMessage());
                        }
                    });

                    break;
                case ZOOM_TO:  //todo allow AnimationOptions
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1)) {
                                throw new JSONException(action + "needs a zoom level");
                            }
                            double zoom = args.getDouble(1);
                            mapCtrl.zoomTo(zoom);
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case GET_CENTER:
                    activity.runOnUiThread(() -> {
                        LatLng latLng = mapCtrl.getCenter();
                        JSONObject json;
                        try {
                            json = new JSONObject()
                                    .put("lat", latLng.getLatitude())
                                    .put("lng", latLng.getLongitude());
                            callbackContext.success(json);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case SET_CENTER:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1)) {
                                throw new JSONException(action + "need a [long, lat] coordinates");
                            }
                            JSONArray center = args.getJSONArray(1);
                            mapCtrl.setCenter(center.getDouble(0), center.getDouble(1));
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case SCROLL_MAP:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1)) {
                                throw new JSONException(action + "need a [x, y] screen coordinates");
                            }
                            JSONArray delta = args.getJSONArray(1);
                            mapCtrl.scrollMap(delta.getLong(0), delta.getLong(1));
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case SET_PITCH:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1)) {
                                throw new JSONException(action + " need a pitch value");
                            }
                            mapCtrl.setTilt(args.getDouble(1));
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case GET_PITCH:
                    activity.runOnUiThread(() -> {
                        try {
                            JSONObject json = new JSONObject().put("pitch", mapCtrl.getTilt());
                            callbackContext.success(json);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case FLY_TO:
                    activity.runOnUiThread(() -> {
                        try {
                            JSONObject options = args.isNull(1) ? null : args.getJSONObject(1);
                            if (options == null || options.isNull("cameraPosition"))
                                callbackContext.error("Need a camera position");
                            else {
                                mapCtrl.flyTo(options.getJSONObject("cameraPosition"));
                                callbackContext.success("Animation started.");
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });

                    break;
                case ADD_MAP_CLICK_CALLBACK:
                    activity.runOnUiThread(() -> {
                        map.markerCallbackContext = callbackContext;
                        mapCtrl.addMapClickCallback(() -> {
                            if (map.markerCallbackContext != null) {
                                try {
                                    JSONObject json = new JSONObject(mapCtrl.getSelecteFeatureCollection());
                                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, json);
                                    pluginResult.setKeepCallback(true);
                                    map.markerCallbackContext.sendPluginResult(pluginResult);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                    callbackContext.error("action " + e.getMessage());
                                }
                            }
                        });
                    });
                    break;
                case ADD_SOURCE:
                    activity.runOnUiThread(() -> {
                        try {
                            final String sourceId = args.getString(1);
                            if (sourceId.isEmpty())
                                throw new JSONException(action + " need a source ID");
                            if (args.isNull(2))
                                throw new JSONException(action + " no source provided");
                            if (!args.getJSONObject(2).getString("type").equals("geojson"))
                                throw new JSONException(action + " only handle GeoJSON");


                            final JSONObject sourceData = args.getJSONObject(2).getJSONObject("data");

                            // We can pass a empty source
                            if (sourceData != null && sourceData.length() > 0) {

                                // Validate GeoJSON data.
                                final GeoJSONObject geoJSON = GeoJSON.parse(args.getJSONObject(2).getJSONObject("data"));

                                // This plugin has limited GeoJSON types. It supports:
                                // - FeatureCollection of point feature
                                // - Single point feature
                                final String sourceType = geoJSON.getType();
                                final boolean isFeatureCollection = sourceType.equals("FeatureCollection");
                                final boolean isFeature = sourceType.equals("Feature");

                                if (!isFeature && !isFeatureCollection) {
                                    throw new JSONException("Only support Feature or FeatureCollection source type");
                                }

                                if (isFeatureCollection) {
                                    final JSONArray features = sourceData.getJSONArray("features");
                                    if (features.length() > 0) {
                                        final String type = features
                                                .getJSONObject(0)
                                                .getJSONObject("geometry")
                                                .getString("type");
                                        if (!type.equals("Point")) {
                                            throw new JSONException("Only support Feature of type Point");
                                        }
                                    }
                                    final FeatureCollection featureCollection = FeatureCollection.fromJson(sourceData.toString());
                                    mapCtrl.addFeatureCollection(sourceId, featureCollection);
                                } else {
                                    final String type = sourceData
                                            .getJSONObject("geometry")
                                            .getString("type");
                                    if (!type.equals("Point")) {
                                        throw new JSONException("Only support Feature of type Point");
                                    }
                                    final Feature feature = Feature.fromJson(sourceData.toString());
                                    mapCtrl.addFeature(sourceId, feature);
                                    callbackContext.success();
                                }
                            } else {
                                mapCtrl.addGeoJsonSource(sourceId);
                                callbackContext.success();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case SET_GEO_JSON:
                    activity.runOnUiThread(() -> {
                        try {
                            final String sourceId = args.getString(1);
                            if (sourceId.isEmpty())
                                throw new JSONException(action + " need a source ID");
                            if (args.isNull(2))
                                throw new JSONException(action + " no geojson data provided");

                            // Validate GeoJSON data.
                            final GeoJSONObject geoJSON = GeoJSON.parse(args.getJSONObject(2));

                            // This plugin has limited GeoJSON types. It supports:
                            // - FeatureCollection of point feature
                            // - Single point feature
                            final JSONObject sourceData = args.getJSONObject(2);
                            final String sourceType = geoJSON.getType();
                            final boolean isFeatureCollection = sourceType.equals("FeatureCollection");
                            final boolean isFeature = sourceType.equals("Feature");

                            if (!isFeature && !isFeatureCollection) {
                                throw new JSONException("Only support Feature or FeatureCollection source type");
                            }

                            if (isFeatureCollection) {
                                final JSONArray features = sourceData.getJSONArray("features");
                                if (features.length() > 0) {
                                    final String type = features
                                            .getJSONObject(0)
                                            .getJSONObject("geometry")
                                            .getString("type");
                                    if (!type.equals("Point")) {
                                        throw new JSONException("Only support Feature of type Point");
                                    }
                                }
                                final FeatureCollection featureCollection = FeatureCollection.fromJson(sourceData.toString());
                                mapCtrl.setSourceGeoJsonData(sourceId, featureCollection);
                            } else {
                                final String type = sourceData
                                        .getJSONObject("geometry")
                                        .getString("type");
                                if (!type.equals("Point")) {
                                    throw new JSONException("Only support Feature of type Point");
                                }
                                final Feature feature = Feature.fromJson(sourceData.toString());
                                mapCtrl.setSourceGeoJsonData(sourceId, feature);
                            }
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case REMOVE_SOURCE:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1))
                                throw new JSONException(action + " no id provided");

                            final JSONObject result = new JSONObject()
                                    .put("success", mapCtrl.removeSource(args.getString(1)));
                            callbackContext.success(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case ADD_LAYER:
                    activity.runOnUiThread(() -> {
                        try {
                            final JSONObject jsonLayer = args.getJSONObject(1);

                            String layerType = jsonLayer.getString("type");
                            if (!layerType.equals("symbol"))
                                throw new JSONException(action + " only symbol layer are currently supported");

                            if (!jsonLayer.has("source"))
                                throw new JSONException(action + " no source provided");

                            final String layerId = jsonLayer.getString("id");
                            final Integer minZoom = jsonLayer.optInt("minzoom", 0);
                            final Integer maxZoom = jsonLayer.optInt("maxzoom", 24);
                            final Expression filter = jsonLayer.has("filter")
                                    ? Expression.Converter.convert(jsonLayer.getJSONArray("filter").toString())
                                    : null;
                            final boolean isRefSource = !jsonLayer.optString("source", "").isEmpty();
                            if (isRefSource) {
                                String beforeId = null;
                                if (args.getString(2) != null && !args.getString(2).equals("null")) {
                                    beforeId = args.getString(2);
                                }
                                final String sourceId = jsonLayer.getString("source");
                                mapCtrl.addSymbolLayer(
                                        layerId,
                                        sourceId,
                                        minZoom,
                                        maxZoom,
                                        filter,
                                        beforeId
                                );
                            }

                            if (jsonLayer.has("layout")) {
                                final JSONObject layout = jsonLayer.getJSONObject("layout");
                                Iterator<String> keys = layout.keys();
                                while (keys.hasNext()) {
                                    String name = keys.next();
                                    switch (name) {
                                        case "icon-image":
                                            final String imageId = layout.getString(name);
                                            mapCtrl.setLayoutPropertyIconImage(layerId, imageId);
                                            break;
                                        case "icon-offset":
                                            final JSONArray jsonOffsetArray = layout.getJSONArray(name);
                                            final Float[] offset = {(float) jsonOffsetArray.getDouble(0), (float) jsonOffsetArray.getDouble(1)};
                                            mapCtrl.setLayoutPropertyOffset(layerId, offset);
                                            break;
                                        case "icon-size":
                                            final float size = (float) layout.getDouble(name);
                                            mapCtrl.setLayoutPropertySize(layerId, size);
                                            break;
                                        case "icon-allow-overlap":
                                            final boolean isOverlap = layout.getBoolean(name);
                                            mapCtrl.setLayoutPropertyIconOverlap(layerId, isOverlap);
                                    }
                                }
                            }
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case REMOVE_LAYER:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1))
                                throw new JSONException(action + " no id provided");

                            final JSONObject result = new JSONObject()
                                    .put("success", mapCtrl.removeLayer(args.getString(1)));
                            callbackContext.success(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case SET_LAYOUT_PROPERTY:
                    activity.runOnUiThread(() -> {
                        try {
                            final String layerId = args.optString(1);
                            if (layerId.isEmpty())
                                throw new JSONException(action + " no layerId provided");

                            final String name = args.optString(2);
                            if (name.isEmpty())
                                throw new JSONException(action + " no property name provided");

                            if (args.isNull(3))
                                throw new JSONException(action + " no value provided");

                            switch (name) {
                                case "icon-image":
                                    final String imageId = args.getString(3);
                                    mapCtrl.setLayoutPropertyIconImage(layerId, imageId);
                                    break;
                                case "icon-offset":
                                    final JSONArray jsonOffsetArray = args.getJSONArray(3);
                                    final Float[] offset = {(float) jsonOffsetArray.getDouble(0), (float) jsonOffsetArray.getDouble(1)};
                                    mapCtrl.setLayoutPropertyOffset(layerId, offset);
                                    break;
                                case "icon-size":
                                    final float size = (float) args.getDouble(3);
                                    mapCtrl.setLayoutPropertySize(layerId, size);
                                    break;
                                case "icon-allow-overlap":
                                    final boolean isOverlap = args.getBoolean(3);
                                    mapCtrl.setLayoutPropertyIconOverlap(layerId, isOverlap);
                            }

                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case ADD_IMAGE:
                    activity.runOnUiThread(() -> {
                        try {
                            final String imageId = args.getString(1);
                            final JSONObject jsonImage = args.getJSONObject(2);

                            if (jsonImage.isNull("width"))
                                throw new JSONException(action + " no width found");

                            if (jsonImage.isNull("height"))
                                throw new JSONException(action + " no height found");

                            if (jsonImage.isNull("uri"))
                                throw new JSONException(action + "no uri found. Cordova Mapbox Plugin does not support ImageData. You need to provide an URI.");

                            mapCtrl.addImage(imageId, jsonImage);
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case REMOVE_IMAGE:
                    activity.runOnUiThread(() -> {
                        try {
                            if (args.isNull(1))
                                throw new JSONException(action + " no id provided");

                            mapCtrl.removeImage(args.getString(1));
                            callbackContext.success();
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case SET_CLICKABLE:
                    activity.runOnUiThread(() -> {
                        try {
                            pluginLayout.setClickable(args.getBoolean(1));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case SET_DEBUG:
                    activity.runOnUiThread(() -> {
                        try {
                            pluginLayout.setDebug(args.getInt(1) != 0);
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case CONVERT_COORDINATES:
                    activity.runOnUiThread(() -> {
                        try {
                            JSONObject coords = args.getJSONObject(1);
                            PointF point = mapCtrl.convertCoordinates(new LatLng(
                                    coords.getDouble("lat"),
                                    coords.getDouble("lng")
                            ));
                            callbackContext.success(new JSONObject("{\"x\": " + point.x + ", \"y\": " + point.y + "}"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case CONVERT_POINT:
                    activity.runOnUiThread(() -> {
                        try {
                            JSONObject point = args.getJSONObject(1);
                            LatLng latLng = mapCtrl.convertPoint(new PointF(
                                    (float) point.getDouble("x"),
                                    (float) point.getDouble("y")
                            ));
                            callbackContext.success(new JSONObject("{\"lat\": " + latLng.getLatitude() + ", \"lng\": " + latLng.getLongitude() + "}"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error("action " + e.getMessage());
                        }
                    });
                    break;
                case ADD_ON_WILL_START_LOADING_MAP_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnWillStartLoadingMapListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_WILL_START_RENDERING_MAP_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnWillStartRenderingMapListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_CAMERA_WILL_CHANGE_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnCameraWillChangeListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_CAMERA_DID_CHANGE_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnCameraDidChangeListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_DID_FINISH_LOADING_STYLE_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnDidFinishLoadingStyleListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_SOURCE_CHANGED_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnSourceChangedListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_WILL_START_RENDERING_FRAME_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnWillStartRenderingFrameListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_DID_FINISH_RENDERING_FRAME_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnDidFinishRenderingFrameListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_DID_FINISH_LOADING_MAP_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnDidFinishLoadingMapListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case ADD_ON_DID_FINISH_RENDERING_MAP_LISTENER:
                    activity.runOnUiThread(() -> mapCtrl.addOnDidFinishRenderingMapListener(() -> {
                        try {
                            PluginResult result = new PluginResult(PluginResult.Status.OK, mapCtrl.getJSONCameraScreenPosition());
                            result.setKeepCallback(true);
                            callbackContext.sendPluginResult(result);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }));
                    break;
                case SET_CONTAINER:
                    activity.runOnUiThread(() -> map.setContainer(args, callbackContext));
                    break;
                case DOWNLOAD_CURRENT_MAP:
                    activity.runOnUiThread(() -> {
                        Runnable startedCallback = () -> {
                            try {
                                JSONObject startedMsg = new JSONObject("{" +
                                        "\"mapDownloadStatus\":{" +
                                        "\"name\": \"" + id + "\"," +
                                        "\"started\": true" +
                                        '}' +
                                        '}');
                                PluginResult result = new PluginResult(PluginResult.Status.OK, startedMsg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                callbackContext.error(e.getMessage());
                            }
                        };

                        Runnable progressCallback = () -> {
                            try {
                                JSONObject progressMsg = new JSONObject("{" +
                                        "\"mapDownloadStatus\":{" +
                                        "\"name\": \"" + id + "\"," +
                                        "\"downloading\":" + mapCtrl.isDownloading() + ',' +
                                        "\"progress\":" + mapCtrl.getDownloadingProgress() +
                                        '}' +
                                        '}');
                                PluginResult result = new PluginResult(PluginResult.Status.OK, progressMsg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                callbackContext.error(e.getMessage());
                            }
                        };

                        Runnable finishedCallback = () -> {
                            try {
                                JSONObject finishedMsg = new JSONObject("{" +
                                        "\"mapDownloadStatus\":{" +
                                        "\"name\": \"" + id + "\"," +
                                        "\"finished\": true" +
                                        '}' +
                                        '}');
                                PluginResult result = new PluginResult(PluginResult.Status.OK, finishedMsg);
                                result.setKeepCallback(true);
                                callbackContext.sendPluginResult(result);
                            } catch (JSONException e) {
                                e.printStackTrace();
                                callbackContext.error(e.getMessage());
                            }
                        };
                        mapCtrl.downloadRegion("" + id, startedCallback, progressCallback, finishedCallback);
                    });
                    break;
                case PAUSE_DOWNLOAD:
                    activity.runOnUiThread(() -> {
                        mapCtrl.pauseDownload();
                        callbackContext.success();
                    });
                    break;
                case GET_OFFLINE_REGIONS_LIST:
                    activity.runOnUiThread(() -> mapCtrl.getOfflineRegions(() -> {
                        ArrayList regionsList = mapCtrl.getOfflineRegionsNames();
                        callbackContext.success(new JSONArray(regionsList));
                    }));
                    break;
                case DELETE_OFFLINE_REGION:
                    activity.runOnUiThread(() -> {
                        try {
                            int regionId = args.getInt(1);
                            mapCtrl.removeOfflineRegion(regionId, () -> {
                                try {
                                    callbackContext.success(new JSONObject("{\"ok\":true}"));
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }

                            });
                        } catch (JSONException e) {
                            callbackContext.error("Need an id region to delete.");
                            e.printStackTrace();
                        }
                    });
                    break;
                case GET_BOUNDS:
                    activity.runOnUiThread(() -> {
                        try {
                            LatLngBounds latLngBounds = mapCtrl.getBounds();
                            callbackContext.success(new JSONObject("{" +
                                    "\"sw\": {" +
                                    "\"lat\":" + latLngBounds.getLatSouth() + ',' +
                                    "\"lng\":" + latLngBounds.getLonWest() +
                                    "}," +
                                    "\"ne\": {" +
                                    "\"lat\":" + latLngBounds.getLatNorth() + ',' +
                                    "\"lng\":" + latLngBounds.getLonEast() +
                                    "}" +
                                    "}"
                            ));
                        } catch (JSONException e) {
                            e.printStackTrace();
                            callbackContext.error(e.getMessage());
                        }
                    });
                    break;
                case GET_CAMERA_POSITION:
                    activity.runOnUiThread(() -> {
                        try {
                            callbackContext.success(mapCtrl.getJSONCameraGeoPosition());
                        } catch (JSONException e) {
                            callbackContext.error(e.getMessage());
                            e.printStackTrace();
                        }
                    });
                    break;
                default:
                    return false;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            callbackContext.error(t.getMessage());
        }
        return true;
    }

    public void onPause(boolean multitasking) {
        MapsManager.onPause();
    }

    public void onResume(boolean multitasking) {
        MapsManager.onResume();
    }

    public void onDestroy() {
        MapsManager.onDestroy();
    }
}