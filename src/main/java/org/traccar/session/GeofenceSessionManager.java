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
package org.traccar.session;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.model.Event;
import org.traccar.model.GeofenceSession;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Date;

@Singleton
public class GeofenceSessionManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GeofenceSessionManager.class);
    
    private final Storage storage;
    
    @Inject
    public GeofenceSessionManager(Storage storage) {
        this.storage = storage;
    }
    
    /**
     * Maneja los eventos de entrada y salida de geozonas
     */
    public void handleGeofenceEvent(Event event) {
        try {
            if (Event.TYPE_GEOFENCE_ENTER.equals(event.getType())) {
                handleGeofenceEnter(event);
            } else if (Event.TYPE_GEOFENCE_EXIT.equals(event.getType())) {
                handleGeofenceExit(event);
            }
        } catch (StorageException e) {
            LOGGER.warn("Error handling geofence event", e);
        }
    }
      /**
     * Maneja la entrada a una geozona
     */
    private void handleGeofenceEnter(Event event) throws StorageException {
        // Verificar si ya existe una sesión abierta para este dispositivo y geozona
        GeofenceSession existingSession = storage.getObject(GeofenceSession.class, new Request(
            new Columns.All(),
            new Condition.And(
                new Condition.And(
                    new Condition.Equals("deviceId", event.getDeviceId()),
                    new Condition.Equals("geofenceId", event.getGeofenceId())
                ),
                new Condition.Compare("exitTime", "IS", "exitTime", null)
            )
        ));
        
        if (existingSession != null) {
            // Ya existe una sesión abierta, actualizar la hora de entrada si es más reciente
            if (event.getEventTime().after(existingSession.getEnterTime())) {
                existingSession.setEnterTime(event.getEventTime());
                existingSession.setEnterPositionId(event.getPositionId());
                storage.updateObject(existingSession, new Request(
                    new Columns.Exclude("id"),
                    new Condition.Equals("id", existingSession.getId())
                ));
            }
            return;
        }
        
        // Crear nueva sesión
        GeofenceSession session = new GeofenceSession();
        session.setDeviceId(event.getDeviceId());
        session.setGeofenceId(event.getGeofenceId());
        session.setEnterTime(event.getEventTime());
        session.setEnterPositionId(event.getPositionId());
        session.setDuration(0);
        
        storage.addObject(session, new Request(new Columns.Exclude("id")));
        
        LOGGER.debug("Created geofence session for device {} entering geofence {}", 
                     event.getDeviceId(), event.getGeofenceId());
    }
      /**
     * Maneja la salida de una geozona
     */
    private void handleGeofenceExit(Event event) throws StorageException {
        // Buscar la sesión abierta más reciente para este dispositivo y geozona
        GeofenceSession session = storage.getObject(GeofenceSession.class, new Request(
            new Columns.All(),
            new Condition.And(
                new Condition.And(
                    new Condition.Equals("deviceId", event.getDeviceId()),
                    new Condition.Equals("geofenceId", event.getGeofenceId())
                ),
                new Condition.Compare("exitTime", "IS", "exitTime", null)
            )
        ));
        
        if (session == null) {
            // No existe sesión abierta, crear una nueva con salida pero sin entrada
            // Esto puede ocurrir si el dispositivo ya estaba en la geozona cuando se inició el sistema
            session = new GeofenceSession();
            session.setDeviceId(event.getDeviceId());
            session.setGeofenceId(event.getGeofenceId());
            session.setEnterTime(event.getEventTime()); // Usar la misma hora como entrada
            session.setExitTime(event.getEventTime());
            session.setExitPositionId(event.getPositionId());
            session.setDuration(0);
            
            storage.addObject(session, new Request(new Columns.Exclude("id")));
            
            LOGGER.debug("Created geofence session for device {} exiting geofence {} (no enter event)", 
                         event.getDeviceId(), event.getGeofenceId());
            return;
        }
        
        // Actualizar la sesión existente con la hora de salida
        session.setExitTime(event.getEventTime());
        session.setExitPositionId(event.getPositionId());
        
        // Calcular duración en milisegundos
        long duration = event.getEventTime().getTime() - session.getEnterTime().getTime();
        session.setDuration(Math.max(0, duration)); // Asegurar que la duración no sea negativa
        
        storage.updateObject(session, new Request(
            new Columns.Exclude("id"),
            new Condition.Equals("id", session.getId())
        ));
        
        LOGGER.debug("Updated geofence session for device {} exiting geofence {}, duration: {} ms", 
                     event.getDeviceId(), event.getGeofenceId(), duration);
    }
    
    /**
     * Finaliza todas las sesiones abiertas para un dispositivo
     * Útil cuando el dispositivo se desconecta o se considera inactivo
     */    public void closeOpenSessions(long deviceId, Date exitTime) {
        try {
            var openSessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.All(),
                new Condition.And(
                    new Condition.Equals("deviceId", deviceId),
                    new Condition.Compare("exitTime", "IS", "exitTime", null)
                )
            ));
            
            for (GeofenceSession session : openSessions) {
                session.setExitTime(exitTime);
                long duration = exitTime.getTime() - session.getEnterTime().getTime();
                session.setDuration(Math.max(0, duration));
                
                storage.updateObject(session, new Request(
                    new Columns.Exclude("id"),
                    new Condition.Equals("id", session.getId())
                ));
            }
            
            if (!openSessions.isEmpty()) {
                LOGGER.debug("Closed {} open geofence sessions for device {}", openSessions.size(), deviceId);
            }
        } catch (StorageException e) {
            LOGGER.warn("Error closing open sessions for device {}", deviceId, e);
        }
    }
}
