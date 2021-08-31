package org.mitre.synthea.importers;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;
import org.yaml.snakeyaml.Yaml;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;


public class IgImporter {

    private static final String[] SYNTHEA_RESOURCE_NAMES = new String[] {"AllergyIntolerance", "Bundle", "CarePlan",
            "Claim", "Condition", "Coverage", "DiagnosticReport", "Encounter", "ExplanationOfBenefit", "Goal",
            "ImagingStudy", "Immunization", "MedicationRequest", "Observation", "Organization", "Patient",
            "Practitioner", "Procedure", "CareTeam", "Device", "DocumentReference", "Location", "Medication",
            "MedicationStatement", "PractitionerRole", "Provenance"};

    private static final HashSet<String> SYNTHEA_RESOURCES = new HashSet<>(Arrays.asList(SYNTHEA_RESOURCE_NAMES));
    private static final String CODE_MAP_PATH = "ig/mapping/mapping.yml";
    protected static boolean TRANSACTION_BUNDLE =
            Config.getAsBoolean("exporter.fhir.transaction_bundle");

    private static HashSet<String> fhirResourcesInIg = new HashSet<>();
    private static HashSet<String> profilesInIg = new HashSet<>();

    private static List<Object> objectsToStub = new ArrayList<>();
    private static Map<String, IgResource> objectsToFill = new HashMap<>();

    private static MappingConfig mappingConfig = new MappingConfig();
    private static Map<String, String> codeToProfileMap = new HashMap<>();
    private static Map<String, String> profileToProfileMap = new HashMap<>();

    public static void convertBundleToIg(Bundle bundle, Person person) {
        importIg();
        importMapping();
        validateMapping();
        convertBundle(bundle, person);
    }

    public static void importIg() {
        File ARTIFACTS = new File(Objects.requireNonNull(ClassLoader
                .getSystemClassLoader()
                .getResource("ig/artifacts")).getFile());

        File[] files = Objects.requireNonNull(ARTIFACTS.listFiles());
        for (File file : files) {
            if (file.isFile()) {
                try {
                    JsonObject object = (JsonObject) JsonParser.parseReader(new FileReader(file));
                    if(isStructureDefinition(object)) {
                        unPackStructureDefinition(object);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void importMapping() {
        File CODE_MAPPING = new File(Objects.requireNonNull(ClassLoader.getSystemClassLoader()
                .getResource(CODE_MAP_PATH)).getFile());

        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine engine = manager.getEngineByName("js");

        try {
            InputStream inputStream = new FileInputStream(CODE_MAPPING);
            Yaml yaml = new Yaml(new org.yaml.snakeyaml.constructor.Constructor(MappingConfig.class));

            mappingConfig = yaml.loadAs(inputStream, MappingConfig.class);
            codeToProfileMap = mappingConfig.codeToProfileMap();
            profileToProfileMap = mappingConfig.getProfileIdentifiers();

            // Proof of concept to evaluate a simple expression
            Map<String, String> expressions = mappingConfig.getExpressions();
            String expression = expressions.get("expression");

            final String text = null;
            engine.put("text", "Test this");
            engine.eval("var stringSplit = " + expression);
            final String stringSplit  = (String) engine.get("stringSplit");
            System.out.println("String: " + stringSplit);
            // End proof of concept //
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // TODO: Support additional validations
    public static void validateMapping() {
        if(!identifiersDisjoint()) {
            System.out.println("Two or more identifiers in your mapping file identify the same profile. " +
                    "Please correct. Exiting...");
            System.exit(0);
        }
        else if(!identifiersCoverAllResourcesInIg()) {
            System.out.println("The identifiers in your mapping file do not match the profiles in your " +
                    "Implementation Guide. Please correct. Exiting...");
            System.exit(0);
        }
    }

    public static boolean identifiersDisjoint() {
        return Collections.disjoint(
                mappingConfig.getCodeableConceptIdentifiers().keySet(), mappingConfig.getProfileIdentifiers().keySet()
        );
    }

    public static boolean identifiersCoverAllResourcesInIg() {
        Set<String> identifiersCombined = Stream.concat(mappingConfig.getCodeableConceptIdentifiers().keySet().stream(),
                mappingConfig.getProfileIdentifiers().keySet().stream()).collect(Collectors.toSet());

        return identifiersCombined.equals(profilesInIg);
    }

    public static void convertBundle(Bundle bundle, Person person) {
        Bundle exportBundle = new Bundle();

        if (TRANSACTION_BUNDLE) {
            bundle.setType(Bundle.BundleType.TRANSACTION);
        } else {
            bundle.setType(Bundle.BundleType.COLLECTION);
        }

        for (Bundle.BundleEntryComponent entry: bundle.getEntry()) {
            Resource resource = entry.getResource();
            String resourceType = resource.getResourceType().toString();

            Class<?> clazz;
            String clazzName = "org.hl7.fhir.r4.model.".concat(resourceType);
            try {
                clazz = Class.forName(clazzName);
                String matchingProfile = null;

                // TODO: Rework this logic
                if (fhirResourcesInIg.contains(resourceType)) {
                    if(profileToProfileMap.containsValue(resourceType)) {
                        matchingProfile = getKeyByValue(profileToProfileMap, resourceType);
                    }
                    else if(classHasMethod(clazz, "getCode")) {
                        Method method = clazz.getMethod("getCode");
                        CodeableConcept codeableConcept = (CodeableConcept) method.invoke(resource);

                        for (Coding coding : codeableConcept.getCoding()) {
                            String code = coding.getCode();
                            matchingProfile = codeToProfileMap.get(code);
                            if(matchingProfile !=null) {
                                break;
                            }
                        }
                    }
                    if (matchingProfile != null) {
                        System.out.println("Matching profile: " + matchingProfile);

                        Meta meta = new Meta();
                        meta.addProfile(objectsToFill.get(matchingProfile).resourceUrl);
                        resource.setMeta(meta);

                        CodeIdentifier code = mappingConfig.getFixedCodeableConcepts().get(matchingProfile);
                        if(code != null &&
                                classHasMethod(clazz, "set" + StringUtils.capitalize(code.elementName.toLowerCase()))) {
                            Method method = clazz.getMethod(
                                    "set" + StringUtils.capitalize(code.elementName.toLowerCase()), List.class);

                            List<CodeableConcept> codeableConcepts = new ArrayList<>();
                            codeableConcepts.add(new CodeableConcept().addCoding(new Coding()
                                            .setSystem(code.system)
                                            .setCode(String.valueOf(code.codes.get(0)))));

                            method.invoke(resource, codeableConcepts);
                            entry.setResource(resource);
                        }
                        exportBundle.addEntry(entry);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // TODO: May want to create a second map where the key is the FHIR Resource Type and the
    //  value is the Profile name instead of calling this function
    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static boolean classHasMethod(Class clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStructureDefinition(JsonObject jsonObj) {
        return jsonObj.has("resourceType") && jsonObj.has("type") &&
                jsonObj.get("resourceType").toString().replace("\"", "").equals("StructureDefinition") &&
                !jsonObj.get("type").toString().replace("\"", "").equals("Extension");
    }

    public static void unPackStructureDefinition(JsonObject jsonObj) {
        String fhirResourceType = jsonObj.get("type").toString().replace("\"", "");
        String resourceUrl = jsonObj.get("url").toString().replace("\"", "");
        String profileName = jsonObj.get("name").toString().replace("\"", "");
        boolean isAbstract = jsonObj.get("abstract").toString().replace("\"", "").equals("true");
        String baseDefinition = jsonObj.get("baseDefinition").toString().replace("\"", "");

        if(SYNTHEA_RESOURCES.contains(fhirResourceType)) {
            if(isAbstract) {
                System.out.println("Is abstract");
            }
            else {
                objectsToFill.put(profileName, new IgResource(profileName, resourceUrl));

                profilesInIg.add(profileName);
                fhirResourcesInIg.add(fhirResourceType);
            }
        }
        else {
            try {
                String clazzName = "org.hl7.fhir.r4.model.".concat(fhirResourceType);
                Class<?> clazz = Class.forName(clazzName);
                Constructor<?> ctor = clazz.getConstructor();
                Resource resource = (Resource) ctor.newInstance();

                Meta meta = new Meta();
                meta.addProfile(resourceUrl);
                Method method = resource.getClass().getMethod("setMeta", Meta.class);
                method.invoke(resource, meta);

                objectsToStub.add(resource);
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
    }
}
