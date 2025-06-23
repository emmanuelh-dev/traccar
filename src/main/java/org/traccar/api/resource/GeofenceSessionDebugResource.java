/*
 * Copyright 2024 - Geofence Session Debug Resource
 */
package org.traccar.api.resource;

import org.traccar.api.BaseResource;
import org.traccar.migration.GeofenceSessionMigration;
import org.traccar.model.GeofenceSession;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Path("debug/geofence-sessions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeofenceSessionDebugResource extends BaseResource {

    @Inject
    private GeofenceSessionMigration migration;

    /**
     * Obtiene estadísticas básicas de la tabla de sesiones
     */
    @GET
    @Path("info")
    public Map<String, Object> getSessionInfo() throws StorageException {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Contar total de sesiones
            Collection<GeofenceSession> allSessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.Include("id")
            ));
            
            // Contar sesiones abiertas (sin exitTime)
            Collection<GeofenceSession> openSessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.Include("id"),
                new org.traccar.storage.query.Condition.Compare("exitTime", "IS", "exitTime", null)
            ));
              // Obtener algunas sesiones de ejemplo
            Collection<GeofenceSession> sampleSessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.All(),
                null,
                new org.traccar.storage.query.Order("enterTime")
            ));
            
            info.put("totalSessions", allSessions.size());
            info.put("openSessions", openSessions.size());
            info.put("closedSessions", allSessions.size() - openSessions.size());
            info.put("sampleSessions", sampleSessions);
            
            // Obtener estadísticas de migración
            GeofenceSessionMigration.MigrationStats migrationStats = migration.getStats();
            info.put("migrationStats", migrationStats);
            
            return info;
            
        } catch (Exception e) {
            info.put("error", e.getMessage());
            info.put("errorType", e.getClass().getSimpleName());
            return info;
        }
    }

    /**
     * Ejecuta la migración de eventos de geofences existentes
     */
    @POST
    @Path("migrate")
    public Response migrateGeofenceEvents() throws StorageException {
        try {
            // Solo permitir a administradores ejecutar la migración
            permissionsService.checkAdmin(getUserId());
            
            int sessionsCreated = migration.migrateGeofenceEvents(null, null);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Migration completed successfully");
            result.put("sessionsCreated", sessionsCreated);
            
            return Response.ok(result).build();
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Migration failed: " + e.getMessage());
            error.put("errorType", e.getClass().getSimpleName());
            
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                         .entity(error)
                         .build();
        }
    }

    /**
     * Verifica si la tabla existe y está accesible
     */
    @GET
    @Path("table-check")
    public Map<String, Object> checkTable() {
        Map<String, Object> result = new HashMap<>();
          try {
            // Intentar hacer una consulta simple a la tabla
            Collection<GeofenceSession> sessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.Include("id")
            ));
            
            result.put("tableExists", true);
            result.put("accessible", true);
            result.put("message", "Table is accessible");
            result.put("hasData", !sessions.isEmpty());
            
        } catch (Exception e) {
            result.put("tableExists", false);
            result.put("accessible", false);
            result.put("error", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
        }
        
        return result;
    }
}
