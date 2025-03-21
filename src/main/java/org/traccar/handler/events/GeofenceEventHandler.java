/*
 * Copyright 2016 - 2024 Anton Tananaev (anton@traccar.org)
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
package org.traccar.handler.events;

import jakarta.inject.Inject;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Calendar;
import org.traccar.model.Event;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.session.cache.CacheManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.traccar.model.User;

public class GeofenceEventHandler extends BaseEventHandler {

    private final CacheManager cacheManager;

    @Inject
    public GeofenceEventHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        if (!PositionUtil.isLatest(cacheManager, position)) {
            return;
        }

        List<Long> oldGeofences = new ArrayList<>();
        Position lastPosition = cacheManager.getPosition(position.getDeviceId());
        if (lastPosition != null && lastPosition.getGeofenceIds() != null) {
            oldGeofences.addAll(lastPosition.getGeofenceIds());
        }

        List<Long> newGeofences = new ArrayList<>();
        if (position.getGeofenceIds() != null) {
            newGeofences.addAll(position.getGeofenceIds());
            newGeofences.removeAll(oldGeofences);
            oldGeofences.removeAll(position.getGeofenceIds());
        }

        for (long geofenceId : oldGeofences) {
            Geofence geofence = cacheManager.getObject(Geofence.class, geofenceId);
            if (geofence != null) {
                long calendarId = geofence.getCalendarId();
                Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
                if ((calendar == null || calendar.checkMoment(position.getFixTime())) && geofence.getNotify()) {
                    // Obtener todos los usuarios asociados al dispositivo
                    Set<User> deviceUsers = cacheManager.getDeviceObjects(position.getDeviceId(), User.class);
                    
                    // Si no hay usuarios, crear un solo evento sin usuario específico
                    if (deviceUsers.isEmpty()) {
                        Event event = new Event(Event.TYPE_GEOFENCE_EXIT, position);
                        event.setGeofenceId(geofenceId);
                        callback.eventDetected(event);
                    } else {
                        // Crear un evento para cada usuario asociado al dispositivo
                        for (User user : deviceUsers) {
                            Event event = new Event(Event.TYPE_GEOFENCE_EXIT, position);
                            event.setGeofenceId(geofenceId);
                            // Asignar el ID del usuario al evento
                            event.set("userId", user.getId());
                            callback.eventDetected(event);
                        }
                    }
                }
            }
        }
        for (long geofenceId : newGeofences) {
            Geofence geofence = cacheManager.getObject(Geofence.class, geofenceId);
            long calendarId = geofence.getCalendarId();
            Calendar calendar = calendarId != 0 ? cacheManager.getObject(Calendar.class, calendarId) : null;
            if ((calendar == null || calendar.checkMoment(position.getFixTime())) && geofence.getNotify()) {
                // Obtener todos los usuarios asociados al dispositivo
                Set<User> deviceUsers = cacheManager.getDeviceObjects(position.getDeviceId(), User.class);
                
                // Si no hay usuarios, crear un solo evento sin usuario específico
                if (deviceUsers.isEmpty()) {
                    Event event = new Event(Event.TYPE_GEOFENCE_ENTER, position);
                    event.setGeofenceId(geofenceId);
                    callback.eventDetected(event);
                } else {
                    // Crear un evento para cada usuario asociado al dispositivo
                    for (User user : deviceUsers) {
                        Event event = new Event(Event.TYPE_GEOFENCE_ENTER, position);
                        event.setGeofenceId(geofenceId);
                        // Asignar el ID del usuario al evento
                        event.set("userId", user.getId());
                        callback.eventDetected(event);
                    }
                }
            }
        }
    }
}
