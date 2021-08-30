package org.mitre.synthea.importers;

public class IgResource {
    public String name;
    public String resourceUrl;

    public IgResource(String name, String resourceUrl) {
        this.name = name;
        this.resourceUrl = resourceUrl;
    }

    @Override
    public String toString() {
        return name;
    }

    public String name() {
        return name;
    }
}
