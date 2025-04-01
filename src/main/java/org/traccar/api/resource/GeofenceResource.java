/*
 * Copyright 2016 - 2017 Anton Tananaev (anton@traccar.org)
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

import org.traccar.api.ExtendedObjectResource;
import org.traccar.model.Geofence;
import org.traccar.session.cache.CacheManager;
import org.traccar.storage.StorageException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Path("geofences")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceResource extends ExtendedObjectResource<Geofence> {

    @Inject
    private CacheManager cacheManager;

    public GeofenceResource() {
        super(Geofence.class, "name");
    }

    @GET
    @Path("byUser")
    public Collection<Geofence> getByUser(@QueryParam("userId") long userId) throws StorageException {
        if (userId > 0) {
            permissionsService.checkUser(getUserId(), userId);
            return cacheManager.getGeofences(userId);
        } else {
            return cacheManager.getGeofences(getUserId());
        }
    }

}
