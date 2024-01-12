package org.inesctec.flexcomm.energyclient.impl.objects;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Instant;

import org.inesctec.flexcomm.energyclient.api.EnergyPeriod;
import org.onosproject.net.AbstractAnnotated;
import org.onosproject.net.Annotations;

public final class DefaultEnergyPeriod extends AbstractAnnotated implements EnergyPeriod {

  private final String emsId;
  private final Instant timestamp;
  private final double flexibility;
  private final double estimate;

  private DefaultEnergyPeriod(String emsId, Instant timestamp, double flexibility,
      double estimate, Annotations annotations) {
    super(annotations);
    this.emsId = emsId;
    this.timestamp = timestamp;
    this.flexibility = flexibility;
    this.estimate = estimate;
  }

  private DefaultEnergyPeriod() {
    this.emsId = null;
    this.timestamp = null;
    this.flexibility = 0.f;
    this.estimate = 0.f;
  }

  public static EnergyPeriod.Builder builder() {
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
  public double flexibility() {
    return this.flexibility;
  }

  @Override
  public double estimate() {
    return this.estimate;
  }

  public static final class Builder implements EnergyPeriod.Builder {
    String emsId = null;
    Instant timestamp = null;
    Double flexibility = null;
    Double estimate = null;
    Annotations annotations;

    private Builder() {
    }

    @Override
    public EnergyPeriod.Builder setEmsId(String emsId) {
      this.emsId = emsId;

      return this;
    }

    @Override
    public EnergyPeriod.Builder setTimestamp(Instant timestamp) {
      this.timestamp = timestamp;

      return this;
    }

    @Override
    public EnergyPeriod.Builder setFlexibility(
        double flexibility) {
      this.flexibility = flexibility;

      return this;
    }

    @Override
    public EnergyPeriod.Builder setEstimate(double estimate) {
      this.estimate = estimate;

      return this;
    }

    @Override
    public EnergyPeriod.Builder setAnnotations(Annotations anns) {
      this.annotations = anns;

      return this;
    }

    @Override
    public EnergyPeriod build() {
      checkNotNull(emsId, "Must specify a ems Id");
      checkNotNull(timestamp, "Must specify a timestamp");
      checkNotNull(flexibility, "Must specify a flexibility array");
      checkNotNull(estimate, "Must specify a estimation array");
      return new DefaultEnergyPeriod(emsId, timestamp, flexibility, estimate, annotations);
    }

  }

}
