package org.inesctec.flexcomm.energy.impl.objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.inesctec.flexcomm.energy.api.Energy;
import org.onosproject.net.AbstractAnnotated;
import org.onosproject.net.Annotations;

public final class DefaultEnergy extends AbstractAnnotated implements Energy {

  private final String emsId;
  private final Instant timestamp;
  private final List<Double> flexibilityArray;
  private final List<Double> estimateArray;

  private DefaultEnergy(String emsId, Instant timestamp, List<Double> flexibilityArray,
      List<Double> estimateArray, Annotations annotations) {
    super(annotations);
    this.emsId = emsId;
    this.timestamp = timestamp;
    this.flexibilityArray = flexibilityArray;
    this.estimateArray = estimateArray;
  }

  private DefaultEnergy() {
    this.emsId = null;
    this.timestamp = null;
    this.flexibilityArray = null;
    this.estimateArray = null;
  }

  public static Energy.Builder builder() {
    return new Builder();
  }

  @Override
  public String emsId() {
    return this.emsId;
  }

  @Override
  public Instant timestamp() {
    return this.timestamp;
  }

  @Override
  public List<Double> flexibilityArray() {
    return this.flexibilityArray;
  }

  @Override
  public List<Double> estimateArray() {
    return this.estimateArray;
  }

  public static final class Builder implements Energy.Builder {
    String emsId = null;
    Instant timestamp = null;
    List<Double> flexibilityArray = new ArrayList<>();
    List<Double> estimateArray = new ArrayList<>();
    Annotations annotations;

    private Builder() {
    }

    @Override
    public Energy.Builder setEmsId(String emsId) {
      this.emsId = emsId;

      return this;
    }

    @Override
    public Energy.Builder setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;

      return this;
    }

    @Override
    public Energy.Builder setFlexibilityArray(
        List<Double> flexibilityArray) {
      this.flexibilityArray = flexibilityArray;

      return this;
    }

    @Override
    public Energy.Builder setEstimateArray(List<Double> estimateArray) {
      this.estimateArray = estimateArray;

      return this;
    }

    @Override
    public Energy.Builder setAnnotations(Annotations anns) {
      this.annotations = anns;

      return this;
    }

    @Override
    public Energy build() {
      checkNotNull(emsId, "Must specify a ems Id");
      checkNotNull(timestamp, "Must specify a timestamp");
      checkArgument(flexibilityArray.size() != 0, "Must specify a flexibility array");
      checkArgument(estimateArray.size() != 0, "Must specify a estimation array");
      return new DefaultEnergy(emsId, timestamp, flexibilityArray, estimateArray, annotations);
    }

  }

}
