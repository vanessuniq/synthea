package org.mitre.synthea.importers;

import java.util.*;

public class MappingConfig {
    private Map<String, CodeIdentifier> codeableConceptIdentifiers = new HashMap<>();
    private Map<String, CodeIdentifier> fixedCodeableConcepts = new HashMap<>();
    private Map<String, String> profileIdentifiers = new HashMap<>();
    private Map<String, String> expressions = new HashMap<>();

    MappingConfig() {}

    public void setCodeableConceptIdentifiers(Map<String, CodeIdentifier> codeableConceptIdentifiers) {
        this.codeableConceptIdentifiers = codeableConceptIdentifiers;
    }

    public void setFixedCodeableConcepts(Map<String, CodeIdentifier> fixedCodeableConcepts) {
        this.fixedCodeableConcepts = fixedCodeableConcepts;
    }

    public void setProfileIdentifiers(Map<String, String> profileIdentifiers) {
        this.profileIdentifiers = profileIdentifiers;
    }

    public CodeIdentifier getCodeableConceptIdentifiers(String name) {
        return this.codeableConceptIdentifiers.get(name);
    }

    public Map<String, CodeIdentifier> getCodeableConceptIdentifiers() {
        return this.codeableConceptIdentifiers;
    }

    public Map<String, CodeIdentifier>  getFixedCodeableConcepts() {
        return this.fixedCodeableConcepts;
    }

    public Map<String, String> getProfileIdentifiers() {
        return this.profileIdentifiers;
    }

    public String getProfileIdentifiers(String name) {
        return this.profileIdentifiers.get(name);
    }

    public Map<String, String> getExpressions() {
        return this.expressions;
    }

    public void setExpressions(Map<String, String> expressions) {
        this.expressions = expressions;
    }

    public Map<String, String> codeToProfileMap() {
        Map<String, String> codeToProfileMap = new HashMap<>();

        for(Map.Entry<String, CodeIdentifier> codeIdentifierMap: codeableConceptIdentifiers.entrySet()) {
            String profileName = codeIdentifierMap.getKey();
            CodeIdentifier codeIdentifier = codeIdentifierMap.getValue();
            List<String> codes = codeIdentifier.getCodes();

            for(int i = 0; i < codes.size(); i++) {
                codeToProfileMap.put(String.valueOf(codes.get(i)), profileName);
            }
        }

        return codeToProfileMap;
    }
}