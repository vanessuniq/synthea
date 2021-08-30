package org.mitre.synthea.importers;

import java.util.List;

public class CodeIdentifier {
    List<String> codes;
    String system;
    String elementName;

    public CodeIdentifier() {
    }

    public List<String> getCodes() {
        return codes;
    }

    public String getElementName() {
        return elementName;
    }

    public String getSystem() {
        return system;
    }

    public void setCodes(List<String> codes) {
        this.codes = codes;
    }

    public void setElementName(String elementName) {
        this.elementName = elementName;
    }

    public void setSystem(String system) {
        this.system = system;
    }
}