package de.geoit.treescoutberlin;

import java.util.List;

public class FeatureInfo {
    private String planungsraum;
    private String bezirksname;
    private String bezirksregion;
    private String prognoseraum;
    private List<?> coordinates;

    // Constructor
    public FeatureInfo(String planungsraum, String bezirksname, String bezirksregion, String prognoseraum, List<?> coordinates) {
        this.planungsraum = planungsraum;
        this.bezirksname = bezirksname;
        this.bezirksregion = bezirksregion;
        this.prognoseraum = prognoseraum;
        this.coordinates = coordinates;
    }

    public String getPlanungsraum() {
        return planungsraum;
    }

    public String getBezirksname() {
        return bezirksname;
    }

    public String getBezirksregion() {
        return bezirksregion;
    }

    public String getPrognoseraum() {
        return prognoseraum;
    }

    public List<?> getCoordinates() {
        return coordinates;
    }
}
