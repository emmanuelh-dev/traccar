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
package org.traccar.reports;

import org.traccar.config.Config;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.model.Device;
import org.traccar.model.GeofenceSession;
import org.traccar.reports.common.ReportUtils;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class GeofenceSessionReportProvider {

    private final Config config;
    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public GeofenceSessionReportProvider(Config config, ReportUtils reportUtils, Storage storage) {
        this.config = config;
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    /**
     * Obtiene las sesiones de geozonas para los parámetros especificados
     */
    public Collection<GeofenceSession> getObjects(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Collection<Long> geofenceIds, Date from, Date to) throws StorageException {
        
        reportUtils.checkPeriodLimit(from, to);

        List<GeofenceSession> result = new ArrayList<>();
        
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            Collection<GeofenceSession> sessions = getSessionsForDevice(device.getId(), geofenceIds, from, to);
            
            // Filtrar por geozonas si se especificaron
            for (GeofenceSession session : sessions) {
                if (geofenceIds.isEmpty() || geofenceIds.contains(session.getGeofenceId())) {
                    // Verificar que el usuario tiene acceso a la geozona
                    if (reportUtils.getObject(userId, org.traccar.model.Geofence.class, session.getGeofenceId()) != null) {
                        result.add(session);
                    }
                }
            }
        }
        
        return result;
    }

    /**
     * Obtiene sesiones de geozonas para un dispositivo específico
     */    private Collection<GeofenceSession> getSessionsForDevice(
            long deviceId, Collection<Long> geofenceIds, Date from, Date to) throws StorageException {
        
        List<Condition> conditions = new ArrayList<>();
        conditions.add(new Condition.Equals("deviceId", deviceId));
        conditions.add(new Condition.Between("enterTime", "from", from, "to", to));
        
        // Since there's no Condition.In, we'll handle geofence filtering at the application level
        // The condition merge will handle multiple AND conditions properly
        
        return storage.getObjects(GeofenceSession.class, new Request(
                new Columns.All(),
                Condition.merge(conditions),
                new Order("enterTime")
        ));
    }

    /**
     * Obtiene sesiones para un dispositivo específico (útil para reportes individuales)
     */
    public Collection<GeofenceSession> getSessionsForDevice(
            long userId, long deviceId, Collection<Long> geofenceIds, Date from, Date to) throws StorageException {
        
        reportUtils.checkPeriodLimit(from, to);

        // Verificar que el usuario tiene acceso al dispositivo
        Device device = reportUtils.getObject(userId, Device.class, deviceId);
        if (device == null) {
            return new ArrayList<>();
        }
        
        Collection<GeofenceSession> sessions = getSessionsForDevice(deviceId, geofenceIds, from, to);
        List<GeofenceSession> result = new ArrayList<>();
        
        // Filtrar por permisos de geozonas
        for (GeofenceSession session : sessions) {
            if (reportUtils.getObject(userId, org.traccar.model.Geofence.class, session.getGeofenceId()) != null) {
                result.add(session);
            }
        }
        
        return result;
    }

    /**
     * Obtiene sesiones actualmente abiertas para un dispositivo
     */
    public Collection<GeofenceSession> getOpenSessionsForDevice(long userId, long deviceId) throws StorageException {
        // Verificar que el usuario tiene acceso al dispositivo
        Device device = reportUtils.getObject(userId, Device.class, deviceId);
        if (device == null) {
            return new ArrayList<>();
        }        Collection<GeofenceSession> sessions = storage.getObjects(GeofenceSession.class, new Request(
                new Columns.All(),
                new Condition.And(
                    new Condition.Equals("deviceId", deviceId),
                    new Condition.Compare("exitTime", "IS", "exitTime", null)
                ),
                new Order("enterTime")
        ));
        
        List<GeofenceSession> result = new ArrayList<>();
        
        // Filtrar por permisos de geozonas
        for (GeofenceSession session : sessions) {
            if (reportUtils.getObject(userId, org.traccar.model.Geofence.class, session.getGeofenceId()) != null) {
                result.add(session);
            }
        }
        
        return result;
    }

    /**
     * Obtiene estadísticas de tiempo en geozonas
     */
    public GeofenceTimeStats getTimeStats(
            long userId, Collection<Long> deviceIds, Collection<Long> groupIds,
            Collection<Long> geofenceIds, Date from, Date to) throws StorageException {
        
        Collection<GeofenceSession> sessions = getObjects(userId, deviceIds, groupIds, geofenceIds, from, to);
        
        long totalTime = 0;
        int totalSessions = 0;
        long longestSession = 0;
        
        for (GeofenceSession session : sessions) {
            totalSessions++;
            long sessionDuration = session.getDuration();
            totalTime += sessionDuration;
            
            if (sessionDuration > longestSession) {
                longestSession = sessionDuration;
            }
        }
        
        return new GeofenceTimeStats(totalTime, totalSessions, longestSession);
    }

    /**
     * Clase para estadísticas de tiempo
     */
    public static class GeofenceTimeStats {
        private final long totalTime;
        private final int totalSessions;
        private final long longestSession;
        
        public GeofenceTimeStats(long totalTime, int totalSessions, long longestSession) {
            this.totalTime = totalTime;
            this.totalSessions = totalSessions;
            this.longestSession = longestSession;
        }
        
        public long getTotalTime() { return totalTime; }
        public int getTotalSessions() { return totalSessions; }
        public long getLongestSession() { return longestSession; }
        public double getAverageTime() { 
            return totalSessions > 0 ? (double) totalTime / totalSessions : 0; 
        }
    }
}
