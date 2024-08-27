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

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response getEnergy(@QueryParam("ems") String emsId, @QueryParam("device") String deviceId) {
    final FlexcommEnergyService service = get(FlexcommEnergyService.class);
    final ObjectNode root = mapper().createObjectNode();
    final ArrayNode rootArrayNode = root.putArray("energy");

    if (emsId != null) {
      final Energy energyEntry = service.getEnergy(emsId);
      if (energyEntry != null) {
        rootArrayNode.add(codec(Energy.class).encode(energyEntry, this));
      }
    } else if (deviceId != null) {
      final Energy energyEntry = service.getEnergy(DeviceId.deviceId(deviceId));
      if (energyEntry != null) {
        rootArrayNode.add(codec(Energy.class).encode(energyEntry, this));
      }
    } else {
      final Iterable<Energy> energyEntries = service.getEnergy();
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
