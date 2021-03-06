package uk.gov.hmcts.reform.bulkscanprocessor.model.out.msg;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OcrField {

    @JsonProperty("metadata_field_name")
    public final String name;

    @JsonProperty("metadata_field_value")
    public final String value;

    @JsonCreator
    public OcrField(
        @JsonProperty("metadata_field_name") String name,
        @JsonProperty("metadata_field_value") String value
    ) {
        this.name = name;
        this.value = value;
    }
}
