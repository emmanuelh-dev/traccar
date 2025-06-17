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
import org.traccar.migration.GeofenceSessionMigration;
import org.traccar.storage.StorageException;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Date;

@Path("admin/migration/geofence-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceSessionMigrationResource extends BaseResource {

    @Inject
    private GeofenceSessionMigration migration;

    @POST
    @Path("migrate")
    public Response migrateEvents(
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        
        // Only allow administrators to run migration
        permissionsService.checkAdmin(getUserId());
        
        int sessionsCreated = migration.migrateGeofenceEvents(from, to);
        
        return Response.ok()
                .entity(new MigrationResult("Migration completed successfully", sessionsCreated))
                .build();
    }

    @POST
    @Path("cleanup")
    public Response cleanupOrphanedSessions() throws StorageException {
        
        // Only allow administrators to run cleanup
        permissionsService.checkAdmin(getUserId());
        
        int deletedCount = migration.cleanupOrphanedSessions();
        
        return Response.ok()
                .entity(new MigrationResult("Cleanup completed successfully", deletedCount))
                .build();
    }

    @GET
    @Path("stats")
    public GeofenceSessionMigration.MigrationStats getStats() throws StorageException {
        
        // Only allow administrators to view stats
        permissionsService.checkAdmin(getUserId());
        
        return migration.getStats();
    }

    /**
     * Result class for migration operations
     */
    public static class MigrationResult {
        private final String message;
        private final int count;
        
        public MigrationResult(String message, int count) {
            this.message = message;
            this.count = count;
        }
        
        public String getMessage() { return message; }
        public int getCount() { return count; }
    }
}
