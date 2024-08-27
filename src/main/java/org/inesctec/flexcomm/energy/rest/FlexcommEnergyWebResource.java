/*
 * Copyright 2024-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.inesctec.flexcomm.energy.rest;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.inesctec.flexcomm.energy.api.Energy;
import org.inesctec.flexcomm.energy.api.EnergyPeriod;
import org.inesctec.flexcomm.energy.api.FlexcommEnergyService;
import org.onosproject.net.DeviceId;
import org.onosproject.rest.AbstractWebResource;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Path("")
public class FlexcommEnergyWebResource extends AbstractWebResource {

  private static final DateFormat REQUEST_TIMESTAMP_FORMATTER = new SimpleDateFormat("yyyy-MM-dd");
  private static final String TIMESTAMP_INVALID = "Timestamp is invalid";

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnergy(@QueryParam("ems") String emsId, @QueryParam("device") String deviceId,
      @QueryParam("timestamp") String timestamp) {
    final FlexcommEnergyService service = get(FlexcommEnergyService.class);
    final ObjectNode root = mapper().createObjectNode();
    final ArrayNode rootArrayNode = root.putArray("energy");

    Instant instant;
    try {
      instant = timestamp != null
          ? REQUEST_TIMESTAMP_FORMATTER.parse(timestamp).toInstant().truncatedTo(ChronoUnit.DAYS)
          : null;
    } catch (ParseException e) {
      throw new IllegalArgumentException(TIMESTAMP_INVALID);
    }

    if (emsId != null) {
      final Energy energyEntry = instant == null ? service.getEnergy(emsId) : service.getEnergy(emsId, instant);
      if (energyEntry != null) {
        rootArrayNode.add(codec(Energy.class).encode(energyEntry, this));
      }
    } else if (deviceId != null) {
      final Energy energyEntry = instant == null ? service.getEnergy(DeviceId.deviceId(deviceId))
          : service.getEnergy(DeviceId.deviceId(deviceId), instant);
      if (energyEntry != null) {
        rootArrayNode.add(codec(Energy.class).encode(energyEntry, this));
      }
    } else {
      final Iterable<Energy> energyEntries = instant == null ? service.getEnergy() : service.getEnergy(instant);
      if (energyEntries != null) {
        energyEntries.forEach(energyEntry -> rootArrayNode.add(codec(Energy.class).encode(energyEntry, this)));
      }
    }

    return ok(root).build();
  }

  @GET
  @Path("current")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getCurrentEnergyPeriod(@QueryParam("ems") String emsId, @QueryParam("device") String deviceId) {
    final FlexcommEnergyService service = get(FlexcommEnergyService.class);
    final ObjectNode root = mapper().createObjectNode();
    final ArrayNode rootArrayNode = root.putArray("energy");

    if (emsId != null) {
      final EnergyPeriod energyEntry = service.getCurrentEnergyPeriod(emsId);
      if (energyEntry != null) {
        rootArrayNode.add(codec(EnergyPeriod.class).encode(energyEntry, this));
      }
    } else if (deviceId != null) {
      final EnergyPeriod energyEntry = service.getCurrentEnergyPeriod(DeviceId.deviceId(deviceId));
      if (energyEntry != null) {
        rootArrayNode.add(codec(EnergyPeriod.class).encode(energyEntry, this));
      }
    } else {
      final Iterable<EnergyPeriod> energyEntries = service.getCurrentEnergyPeriod();
      if (energyEntries != null) {
        energyEntries.forEach(energyEntry -> rootArrayNode.add(codec(EnergyPeriod.class).encode(energyEntry, this)));
      }
    }

    return ok(root).build();
  }

}
