package org.inesctec.flexcomm.energy.rest;

import static org.slf4j.LoggerFactory.getLogger;

import org.inesctec.flexcomm.energy.api.Energy;
import org.inesctec.flexcomm.energy.api.EnergyPeriod;
import org.onosproject.codec.CodecService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

@Component(immediate = true)
public class FlexcommEnergyCodecRegistrator {

  private final Logger log = getLogger(getClass());

  @Reference(cardinality = ReferenceCardinality.MANDATORY)
  protected CodecService codecService;

  @Activate
  public void activate() {
    codecService.registerCodec(Energy.class, new EnergyCodec());
    codecService.registerCodec(EnergyPeriod.class, new EnergyPeriodCodec());

    log.info("Started");
  }

  @Deactivate
  public void deactivate() {
    codecService.unregisterCodec(Energy.class);
    codecService.unregisterCodec(EnergyPeriod.class);

    log.info("Stopped");
  }

}
