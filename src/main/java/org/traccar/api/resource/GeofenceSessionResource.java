/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.api.resource;

import org.traccar.api.BaseResource;
import org.traccar.model.GeofenceSession;
import org.traccar.model.UserRestrictions;
import org.traccar.reports.GeofenceSessionReportProvider;
import org.traccar.storage.StorageException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Path("reports/geofence-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceSessionResource extends BaseResource {

    @Inject
    private GeofenceSessionReportProvider geofenceSessionReportProvider;    @GET
    public Collection<GeofenceSession> getGeofenceSessions(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("geofenceId") List<Long> geofenceIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        return geofenceSessionReportProvider.getObjects(
                getUserId(), deviceIds, groupIds, geofenceIds, from, to);
    }    @GET
    @Path("device")
    public Collection<GeofenceSession> getGeofenceSessionsForDevice(
            @QueryParam("deviceId") long deviceId,
            @QueryParam("geofenceId") List<Long> geofenceIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        return geofenceSessionReportProvider.getSessionsForDevice(
                getUserId(), deviceId, geofenceIds, from, to);
    }    @GET
    @Path("open")
    public Collection<GeofenceSession> getOpenGeofenceSessions(
            @QueryParam("deviceId") long deviceId) throws StorageException {
        
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        return geofenceSessionReportProvider.getOpenSessionsForDevice(getUserId(), deviceId);
    }    @GET
    @Path("stats")
    public GeofenceSessionReportProvider.GeofenceTimeStats getGeofenceStats(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("geofenceId") List<Long> geofenceIds,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        return geofenceSessionReportProvider.getTimeStats(
                getUserId(), deviceIds, groupIds, geofenceIds, from, to);
    }
}
