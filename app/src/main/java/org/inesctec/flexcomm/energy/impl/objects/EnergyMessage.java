package org.inesctec.flexcomm.energy.impl.objects;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnergyMessage implements Serializable {

  @JsonProperty("timestamp")
  private String timestamp;

  @JsonProperty("emsId")
  private String emsId;

  @JsonProperty("flexArrayConsumption")
  private List<Double> flexArrayConsumption;

  @JsonProperty("flexArrayEstimate")
  private List<Double> flexArrayEstimate;

  public String getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(String timestamp) {
    this.timestamp = timestamp;
  }

  public String getEmsId() {
    return emsId;
  }

  public void setEmsId(String emsId) {
    this.emsId = emsId;
  }

  public List<Double> getFlexArrayConsumption() {
    return flexArrayConsumption;
  }

  public void setFlexArrayConsumption(List<Double> flexArrayConsumption) {
    this.flexArrayConsumption = flexArrayConsumption;
  }

  public List<Double> getFlexArrayEstimate() {
    return flexArrayEstimate;
  }

  public void setFlexArrayEstimate(List<Double> flexArrayEstimate) {
    this.flexArrayEstimate = flexArrayEstimate;
  }

  @Override
  public String toString() {
    return "EnergyMessage [timestamp=" + timestamp + ", emsId=" + emsId + ", flexArrayConsumption="
        + flexArrayConsumption + ", flexArrayEstimate=" + flexArrayEstimate + "]";
  }
}
