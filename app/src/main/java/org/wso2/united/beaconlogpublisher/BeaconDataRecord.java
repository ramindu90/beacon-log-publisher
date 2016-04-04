package org.wso2.united.beaconlogpublisher;

public class BeaconDataRecord {

    private String uuid;
    private String major;
    private String minor;
    private double distance;
    private int rssi;
    private String proximity;
    private String beaconType; //todo: how?
    private String allData;

    //todo: automate the values
    private String airportCode = "ORD";
    private String eventType = "ENTER";
    private double fenceAccuracy = 0.0d;
    private int fenceAltitude = 0;
    private double fenceBearing = 0.0d;
    private String fenceIdentifier = "";
    private double fenceLatitude = 0.0d;
    private double fenceLongitude = 0.0d;
    private double fenceSpeed = 0.0d;
    private String name; //todo: how?
    private long timestamp;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getMinor() {
        return minor;
    }

    public void setMinor(String minor) {
        this.minor = minor;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }

    public String getProximity() {
        return proximity;
    }

    public void setProximity(String proximity) {
        this.proximity = proximity;
    }

    public String getBeaconType() {
        return beaconType;
    }

    public void setBeaconType(String beaconType) {
        this.beaconType = beaconType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAirportCode() {
        return airportCode;
    }

    public void setAirportCode(String airportCode) {
        this.airportCode = airportCode;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public double getFenceAccuracy() {
        return fenceAccuracy;
    }

    public void setFenceAccuracy(double fenceAccuracy) {
        this.fenceAccuracy = fenceAccuracy;
    }

    public int getFenceAltitude() {
        return fenceAltitude;
    }

    public void setFenceAltitude(int fenceAltitude) {
        this.fenceAltitude = fenceAltitude;
    }

    public double getFenceBearing() {
        return fenceBearing;
    }

    public void setFenceBearing(double fenceBearing) {
        this.fenceBearing = fenceBearing;
    }

    public String getFenceIdentifier() {
        return fenceIdentifier;
    }

    public void setFenceIdentifier(String fenceIdentifier) {
        this.fenceIdentifier = fenceIdentifier;
    }

    public double getFenceLatitude() {
        return fenceLatitude;
    }

    public void setFenceLatitude(double fenceLatitude) {
        this.fenceLatitude = fenceLatitude;
    }

    public double getFenceLongitude() {
        return fenceLongitude;
    }

    public void setFenceLongitude(double fenceLongitude) {
        this.fenceLongitude = fenceLongitude;
    }

    public double getFenceSpeed() {
        return fenceSpeed;
    }

    public void setFenceSpeed(double fenceSpeed) {
        this.fenceSpeed = fenceSpeed;
    }

    public String getAllData() {
        return allData;
    }

    public void setAllData(String allData) {
        this.allData = allData;
    }
}
