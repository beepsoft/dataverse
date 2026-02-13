/**
 * This work was implemented by HUN-REN SZTAKI DSD (https://dsd.sztaki.hu) and sponsored by the
 * Hungarian national research data project HUN-REN ARP (https://researchdata.hu/en).
 *
 * @author Balazs E. Pataki (pataki@sztaki.hu)
 * @author Norbert Finta (finta@sztaki.hu)
 */
package edu.harvard.iq.dataverse.api.cedar;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.gson.*;
import edu.harvard.iq.dataverse.api.cedar.util.JsonHelper;
import edu.harvard.iq.dataverse.cedar.CedarServiceBean;
import edu.harvard.iq.dataverse.cedar.DataverseControlledVocabulary;
import edu.harvard.iq.dataverse.cedar.DataverseDatasetField;
import edu.harvard.iq.dataverse.cedar.DataverseMetadataBlock;

import java.io.IOException;
import java.util.*;

import static edu.harvard.iq.dataverse.api.cedar.util.JsonHelper.*;

/**
 * Converts CEDAR template JSON into Dataverse metadata block (MDB) definitions,
 * dataset field definitions, and controlled vocabulary values.
 * <p>
 * The converter reads a CEDAR template, extracts structure and metadata
 * (including field types, labels, constraints, and vocabulary entries),
 * and produces Dataverse-compatible TSV output for:
 * <ul>
 *   <li>metadata blocks</li>
 *   <li>dataset fields</li>
 *   <li>controlled vocabularies</li>
 * </ul>.
 */
public class CedarTemplateToDvMdbConverter {

    private String language;
    private final CedarServiceBean cedarService;

    public CedarTemplateToDvMdbConverter(CedarServiceBean cedarService) {
        this("en", cedarService);
    }

    public CedarTemplateToDvMdbConverter(String language, CedarServiceBean cedarService)
    {
        if (language == null) {
            this.language = "en";
        }
        else {
            this.language = language;
        }
        this.cedarService = cedarService;
    }

    public String processCedarTemplate(String cedarTemplate, Set<String> overridePropNames) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        CsvMapper mapper = new CsvMapper();

        JsonObject cedarTemplateJson = gson.fromJson(cedarTemplate, JsonObject.class);

        String metadataBlockId = cedarTemplateJson.get("schema:identifier").getAsString();

        DataverseMetadataBlock metadataBlockValues = processMetadataBlock(cedarTemplateJson, metadataBlockId);
        ProcessedCedarTemplateValues processedCedarTemplateValues = processTemplate(cedarTemplateJson, metadataBlockId, new ProcessedCedarTemplateValues(new ArrayList<>(), new ArrayList<>()), null, overridePropNames);

        CsvSchema mdbSchema = CsvSchema.builder()
                .addColumn("#metadataBlock")
                .addColumn("name")
                .addColumn("dataverseAlias")
                .addColumn("displayName")
                .addColumn("blockURI")
                .build()
                .withHeader()
                .withColumnSeparator('\t')
                .withoutQuoteChar();

        CsvSchema datasetFieldSchema = CsvSchema.builder()
                .addColumn("#datasetField")
                .addColumn("name")
                .addColumn("title")
                .addColumn("description")
                .addColumn("watermark")
                .addColumn("fieldType")
                .addColumn("displayOrder")
                .addColumn("displayFormat")
                .addColumn("advancedSearchField")
                .addColumn("allowControlledVocabulary")
                .addColumn("allowmultiples")
                .addColumn("facetable")
                .addColumn("displayoncreate")
                .addColumn("required")
                .addColumn("parent")
                .addColumn("metadatablock_id")
                .addColumn("termURI")
                .build()
                .withHeader()
                .withColumnSeparator('\t')
                .withoutQuoteChar()
                .withoutEscapeChar();

        CsvSchema controlledVocabularySchema = CsvSchema.builder()
                .addColumn("#controlledVocabulary")
                .addColumn("DatasetField")
                .addColumn("Value")
                .addColumn("identifier")
                .addColumn("displayOrder")
                .build()
                .withHeader()
                .withColumnSeparator('\t')
                .withoutQuoteChar()
                .withoutEscapeChar();

        String metadataBlocks = mapper.writer(mdbSchema).writeValueAsString(metadataBlockValues);
        String datasetFieldValues = mapper.writer(datasetFieldSchema).writeValueAsString(processedCedarTemplateValues.datasetFieldValues);
        String controlledVocabularyValues = mapper.writer(controlledVocabularySchema).writeValueAsString(processedCedarTemplateValues.controlledVocabularyValues);

        return metadataBlocks + datasetFieldValues + controlledVocabularyValues;
    }

    public DataverseMetadataBlock processMetadataBlock(JsonObject cedarTemplate, String metadataBlockId) {
        DataverseMetadataBlock dataverseMetadataBlock = new DataverseMetadataBlock();

        dataverseMetadataBlock.setName(metadataBlockId);
        dataverseMetadataBlock.setDisplayName(cedarTemplate.get("schema:name").getAsString());
//        dataverseMetadataBlock.setBlockURI(cedarTemplate.get("@id").getAsString());
        try {
            JsonElement dvBlockUri = JsonHelper.getJsonElement(cedarTemplate, "properties.@type.oneOf[0].enum[0]");
            if (dvBlockUri != null) {
                dataverseMetadataBlock.setBlockURI(dvBlockUri.getAsString());
            }
        } catch (Exception e) {
            // There was no blockUri provided for the mdb
        }
        return dataverseMetadataBlock;
    }

    public ProcessedCedarTemplateValues processTemplate(JsonObject cedarTemplate, String metadataBlockId, ProcessedCedarTemplateValues processedCedarTemplateValues, String parentName, Set<String> overridePropNames) {
        getStringList(cedarTemplate, "_ui.order").stream().filter(propertyName -> !overridePropNames.contains(propertyName)).forEach(propertyName -> {
            JsonObject property = getJsonObject(cedarTemplate, "properties." + propertyName);
            String propertyType = Optional.ofNullable(property.get("@type")).map(JsonElement::getAsString).orElse(null);
            int displayOrder = processedCedarTemplateValues.datasetFieldValues.size();
            String propertyTermUri = getStringList(cedarTemplate, "properties.@context.properties." + propertyName + ".enum").get(0);

            if (propertyType != null) {
                String actPropertyType = propertyType.substring(propertyType.lastIndexOf("/") + 1);
                boolean isHidden = Optional.ofNullable(property.getAsJsonObject("_ui").get("hidden")).map(JsonElement::getAsBoolean).orElse(false);
                if (!isHidden && (actPropertyType.equals("TemplateField") || actPropertyType.equals("StaticTemplateField"))) {
                    JsonObject valueConstraints = property.getAsJsonObject("_valueConstraints");
                    boolean allowMultiple =
                            (valueConstraints.has("multipleChoice") && valueConstraints.get("multipleChoice").getAsBoolean())
                            || (property.has("minItems") ||  property.has("maxItems"));
                    processTemplateField(property, displayOrder, allowMultiple, metadataBlockId, propertyTermUri, parentName, processedCedarTemplateValues);
                } else if (actPropertyType.equals("TemplateElement")) {
                    processTemplateElement(property, processedCedarTemplateValues, metadataBlockId, propertyTermUri, false, parentName, overridePropNames);
                }
            } else {
                String actPropertyType = property.get("type").getAsString();
                if (actPropertyType.equals("array")) {
                    processArray(property, processedCedarTemplateValues, metadataBlockId, propertyTermUri, parentName, overridePropNames);
                }
            }
        });

        return processedCedarTemplateValues;
    }

    public void processTemplateField(JsonObject templateField, int displayOrder, boolean allowMultiple, String metadataBlockName, String propertyTermUri, String parentName, ProcessedCedarTemplateValues processedCedarTemplateValues) {
        DataverseDatasetField dataverseDatasetField = new DataverseDatasetField();
        String fieldType = Optional.ofNullable(getJsonElement(templateField, "_ui.inputType")).map(JsonElement::getAsString).orElse(null);
        boolean allowCtrlVocab = Objects.equals(fieldType, "list") || Objects.equals(fieldType, "radio");
        boolean hasExternalVocabValues = cedarService.hasExternalValues(templateField);
        String displayFormat = Optional.ofNullable(getJsonElement(templateField, "_ext.dataverse.displayFormat")).map(JsonElement::getAsString).orElse(null);
        String watermark = Optional.ofNullable(getJsonElement(templateField, "_ext.dataverse.watermark")).map(JsonElement::getAsString).orElse(null);

        /*
         * fieldnames can not contain dots in CEDAR, so we replace them with colons before exporting the template
         * upon importing from CEDAR the colons are replaced with dots again
         * */
        dataverseDatasetField.setName(Optional.ofNullable(templateField.get("schema:name")).map(name -> name.getAsString().replace(':', '.')).orElse(null));
        String title = Optional.ofNullable(templateField.get("skos:prefLabel")).map(JsonElement::getAsString).orElse(templateField.get("schema:name").getAsString());
        dataverseDatasetField.setTitle(title);
        dataverseDatasetField.setDescription(Optional.ofNullable(templateField.get("schema:description")).map(JsonElement::getAsString).orElse(null));
        dataverseDatasetField.setFieldType(getDataverseFieldType(templateField));
        dataverseDatasetField.setDisplayOrder(displayOrder);
        // We need to set allowControlledVocabulary to true for datasetFieldTypes with external vocabulary values as well,
        // to prevent the edu.harvard.iq.dataverse.DatasetField.createNewEmptyDatasetField(edu.harvard.iq.dataverse.DatasetFieldType)
        // adding a default datasetFieldValue to the datasetField
        dataverseDatasetField.setAllowControlledVocabulary(allowCtrlVocab || hasExternalVocabValues);
        dataverseDatasetField.setAllowmultiples(allowMultiple);
        dataverseDatasetField.setDisplayoncreate(Optional.ofNullable(getJsonElement(templateField, "_ext.dataverse.displayoncreate")).map(JsonElement::getAsBoolean).orElse(false));
        dataverseDatasetField.setFacetable(Optional.ofNullable(getJsonElement(templateField, "_ext.dataverse.facetable")).map(JsonElement::getAsBoolean).orElse(false));
        dataverseDatasetField.setAdvancedSearchField(Optional.ofNullable(getJsonElement(templateField, "_ext.dataverse.advancedSearchField")).map(JsonElement::getAsBoolean).orElse(false));
        dataverseDatasetField.setRequired(Optional.ofNullable(getJsonElement(templateField, "_valueConstraints.requiredValue")).map(JsonElement::getAsBoolean).orElse(false));
        dataverseDatasetField.setParent(parentName);
        dataverseDatasetField.setmetadatablock_id(metadataBlockName);
        dataverseDatasetField.setTermUri(propertyTermUri);
        
        if (displayFormat != null) {
            dataverseDatasetField.setDisplayFormat(displayFormat);
        }

        if (watermark != null) {
            dataverseDatasetField.setWatermark(watermark);
        }

        processedCedarTemplateValues.datasetFieldValues.add(dataverseDatasetField);

        if (allowCtrlVocab) {
            String finalPropName = dataverseDatasetField.getName();
            processCtrlVocabValues(templateField, finalPropName, processedCedarTemplateValues);
        }
    }

    public void processCtrlVocabValues(JsonObject templateField, String finalPropName, ProcessedCedarTemplateValues processedCedarTemplateValues) {
        JsonArray ctrlVocabValues = JsonHelper.getJsonElement(templateField, "_valueConstraints.literals").getAsJsonArray();

        for (int i = 0; i < ctrlVocabValues.size(); i++) {
            JsonObject value = ctrlVocabValues.get(i).getAsJsonObject();
            DataverseControlledVocabulary controlledVocabulary = new DataverseControlledVocabulary();
            controlledVocabulary.setDatasetField(finalPropName);
            controlledVocabulary.setValue(value.get("label").getAsString());
            controlledVocabulary.setDisplayOrder(i);
            processedCedarTemplateValues.controlledVocabularyValues.add(controlledVocabulary);
        }
    }

    public void processTemplateElement(JsonObject templateElement, ProcessedCedarTemplateValues processedCedarTemplateValues, String metadataBlockId, String propertyTermUri, boolean allowMultiples, String parentName, Set<String> overridePropNames) {
        int displayOrder = processedCedarTemplateValues.datasetFieldValues.size();
        boolean allowsMultiple = allowMultiples || templateElement.keySet().contains("minItems") || templateElement.keySet().contains("maxItems");
        processTemplateField(templateElement, displayOrder, allowsMultiple, metadataBlockId, propertyTermUri, parentName, processedCedarTemplateValues);
        String parent = processedCedarTemplateValues.datasetFieldValues.get(processedCedarTemplateValues.datasetFieldValues.size() - 1).getName();
        processTemplate(templateElement, metadataBlockId, processedCedarTemplateValues, parent, overridePropNames);
    }

    public void processArray(JsonObject array, ProcessedCedarTemplateValues processedCedarTemplateValues, String metadataBlockId, String propertyTermUri, String parentName, Set<String> overridePropNames) {
        JsonObject items = array.getAsJsonObject("items");
        int displayOrder = processedCedarTemplateValues.datasetFieldValues.size();
        String inputType = Optional.ofNullable(getJsonElement(items, "_ui.inputTye")).map(JsonElement::getAsString).orElse(null);
        if (inputType != null) {
            processTemplateField(items, displayOrder, true, metadataBlockId, propertyTermUri, parentName, processedCedarTemplateValues);
        } else {
            processTemplateElement(items, processedCedarTemplateValues, metadataBlockId, propertyTermUri, true, parentName, overridePropNames);
        }
    }

    public String getDataverseFieldType(JsonObject templateField) {
        Map<String, String> cedarDataverseFieldTypes = Map.of(
            "textfield", "text",
            "temporal", "date",
            "numeric", "int-float",
            "richtext", "textbox",
            "textarea", "textbox",
            "link", "url",
            "list", "text",
            "radio", "text",
            "attribute-value", "text",
            "email", "email"
        );

        String dataverseFieldType = null;
        String fieldType = Optional.ofNullable(getJsonElement(templateField, "_ui.inputType")).map(JsonElement::getAsString).orElse(null);

        if (fieldType != null && cedarDataverseFieldTypes.containsKey(fieldType)) {
            if (Objects.equals(fieldType, "numeric")) {
                String numericType = Optional.ofNullable(getJsonElement(templateField, "_valueConstraints.numberType")).map(JsonElement::getAsString).orElse(null);
                if (Objects.equals(numericType, "xsd:decimal")) {
                    dataverseFieldType = cedarDataverseFieldTypes.get(fieldType).split("-")[0];
                } else {
                    dataverseFieldType = cedarDataverseFieldTypes.get(fieldType).split("-")[1];
                }
            } else {
                dataverseFieldType = cedarDataverseFieldTypes.get(fieldType);
            }
        } else {
            dataverseFieldType = "none";
        }

        return dataverseFieldType;
    }

    private static class ProcessedCedarTemplateValues {
        private ArrayList<DataverseDatasetField> datasetFieldValues;
        private ArrayList<DataverseControlledVocabulary> controlledVocabularyValues;

        public ProcessedCedarTemplateValues(ArrayList<DataverseDatasetField> datasetFieldValues, ArrayList<DataverseControlledVocabulary> controlledVocabularyValues) {
            this.datasetFieldValues = datasetFieldValues;
            this.controlledVocabularyValues = controlledVocabularyValues;
        }

        public ArrayList<DataverseDatasetField> getDatasetFieldValues() {
            return datasetFieldValues;
        }

        public void setDatasetFieldValues(ArrayList<DataverseDatasetField> datasetFieldValues) {
            this.datasetFieldValues = datasetFieldValues;
        }

        public ArrayList<DataverseControlledVocabulary> getControlledVocabularyValues() {
            return controlledVocabularyValues;
        }

        public void setControlledVocabularyValues(ArrayList<DataverseControlledVocabulary> controlledVocabularyValues) {
            this.controlledVocabularyValues = controlledVocabularyValues;
        }
    }
}
