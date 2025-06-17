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
package org.traccar.migration;

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
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.*;

/**
 * Migration utility to populate the new tc_geofence_sessions table from existing geofence events
 */
@Singleton
public class GeofenceSessionMigration {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(GeofenceSessionMigration.class);
    
    private final Storage storage;
    
    @Inject
    public GeofenceSessionMigration(Storage storage) {
        this.storage = storage;
    }
    
    /**
     * Migrates geofence events from the events table to the sessions table
     * This method processes existing geofence enter/exit events and creates corresponding sessions
     * 
     * @param fromDate Optional start date for migration (null for all data)
     * @param toDate Optional end date for migration (null for all data)
     * @return Number of sessions created
     */
    public int migrateGeofenceEvents(Date fromDate, Date toDate) throws StorageException {
        LOGGER.info("Starting geofence events migration...");
          // Get all geofence events ordered by device, geofence, and time
        List<Condition> conditions = new ArrayList<>();
        
        // Since Condition.In doesn't exist, we'll use OR conditions for event types
        Condition typeCondition = new Condition.Or(
            new Condition.Equals("type", Event.TYPE_GEOFENCE_ENTER),
            new Condition.Equals("type", Event.TYPE_GEOFENCE_EXIT)
        );
        conditions.add(typeCondition);
        
        if (fromDate != null) {
            conditions.add(new Condition.Compare("eventTime", ">=", "from", fromDate));
        }
        if (toDate != null) {
            conditions.add(new Condition.Compare("eventTime", "<=", "to", toDate));
        }
        
        Collection<Event> events = storage.getObjects(Event.class, new Request(
            new Columns.All(),
            Condition.merge(conditions),
            new Order("deviceId")
        ));
        
        LOGGER.info("Found {} geofence events to process", events.size());
        
        // Group events by device and geofence
        Map<String, List<Event>> eventGroups = new HashMap<>();
        for (Event event : events) {
            String key = event.getDeviceId() + "_" + event.getGeofenceId();
            eventGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }
        
        int sessionsCreated = 0;
        
        // Process each device-geofence combination
        for (Map.Entry<String, List<Event>> entry : eventGroups.entrySet()) {
            String[] keyParts = entry.getKey().split("_");
            long deviceId = Long.parseLong(keyParts[0]);
            long geofenceId = Long.parseLong(keyParts[1]);
            
            List<Event> deviceGeofenceEvents = entry.getValue();
            sessionsCreated += processEventSequence(deviceId, geofenceId, deviceGeofenceEvents);
        }
        
        LOGGER.info("Migration completed. Created {} sessions", sessionsCreated);
        return sessionsCreated;
    }
    
    /**
     * Processes a sequence of events for a specific device-geofence combination
     */
    private int processEventSequence(long deviceId, long geofenceId, List<Event> events) throws StorageException {
        int sessionsCreated = 0;
        Event pendingEnter = null;
        
        for (Event event : events) {
            if (Event.TYPE_GEOFENCE_ENTER.equals(event.getType())) {
                if (pendingEnter != null) {
                    // We have an enter without a corresponding exit - close the previous session
                    createSession(pendingEnter, null);
                    sessionsCreated++;
                    LOGGER.debug("Created incomplete session for device {} geofence {} (enter without exit)", 
                                deviceId, geofenceId);
                }
                pendingEnter = event;
                
            } else if (Event.TYPE_GEOFENCE_EXIT.equals(event.getType())) {
                if (pendingEnter != null) {
                    // We have a matching enter-exit pair
                    createSession(pendingEnter, event);
                    sessionsCreated++;
                    pendingEnter = null;
                    LOGGER.debug("Created complete session for device {} geofence {}", deviceId, geofenceId);
                } else {
                    // We have an exit without a corresponding enter - create session with exit only
                    createSession(null, event);
                    sessionsCreated++;
                    LOGGER.debug("Created exit-only session for device {} geofence {}", deviceId, geofenceId);
                }
            }
        }
        
        // Handle any remaining pending enter event
        if (pendingEnter != null) {
            createSession(pendingEnter, null);
            sessionsCreated++;
            LOGGER.debug("Created enter-only session for device {} geofence {}", deviceId, geofenceId);
        }
        
        return sessionsCreated;
    }
    
    /**
     * Creates a geofence session from enter and/or exit events
     */
    private void createSession(Event enterEvent, Event exitEvent) throws StorageException {
        GeofenceSession session = new GeofenceSession();
        
        if (enterEvent != null) {
            session.setDeviceId(enterEvent.getDeviceId());
            session.setGeofenceId(enterEvent.getGeofenceId());
            session.setEnterTime(enterEvent.getEventTime());
            session.setEnterPositionId(enterEvent.getPositionId());
        } else {
            // Exit-only session
            session.setDeviceId(exitEvent.getDeviceId());
            session.setGeofenceId(exitEvent.getGeofenceId());
            session.setEnterTime(exitEvent.getEventTime()); // Use exit time as enter time
        }
        
        if (exitEvent != null) {
            session.setExitTime(exitEvent.getEventTime());
            session.setExitPositionId(exitEvent.getPositionId());
            
            // Calculate duration
            if (enterEvent != null) {
                long duration = exitEvent.getEventTime().getTime() - enterEvent.getEventTime().getTime();
                session.setDuration(Math.max(0, duration));
            } else {
                session.setDuration(0);
            }
        } else {
            // Enter-only session (still open)
            session.setDuration(0);
        }
          // Check if session already exists to avoid duplicates
        GeofenceSession existingSession = storage.getObject(GeofenceSession.class, new Request(
            new Columns.All(),
            new Condition.And(
                new Condition.And(
                    new Condition.Equals("deviceId", session.getDeviceId()),
                    new Condition.Equals("geofenceId", session.getGeofenceId())
                ),
                new Condition.Equals("enterTime", session.getEnterTime())
            )
        ));
        
        if (existingSession == null) {
            storage.addObject(session, new Request(new Columns.Exclude("id")));
        } else {
            LOGGER.debug("Session already exists, skipping duplicate");
        }
    }
      /**
     * Cleans up orphaned sessions (sessions without corresponding geofences or devices)
     * Note: This simplified version will need manual cleanup or database-specific queries
     */
    public int cleanupOrphanedSessions() throws StorageException {
        LOGGER.info("Starting cleanup of orphaned sessions...");
        
        // For now, we'll skip the complex cleanup that requires raw SQL
        // This would need to be implemented with database-specific logic
        LOGGER.warn("Orphaned session cleanup not implemented - requires database-specific queries");
        
        return 0;
    }
      /**
     * Gets migration statistics
     */
    public MigrationStats getStats() throws StorageException {
        // Count total events
        var enterEvents = storage.getObjects(Event.class, new Request(
            new Columns.Include("id"),
            new Condition.Equals("type", Event.TYPE_GEOFENCE_ENTER)
        ));
        
        var exitEvents = storage.getObjects(Event.class, new Request(
            new Columns.Include("id"),
            new Condition.Equals("type", Event.TYPE_GEOFENCE_EXIT)
        ));
        
        // Count sessions
        var totalSessions = storage.getObjects(GeofenceSession.class, new Request(
            new Columns.Include("id")
        ));
        
        var openSessions = storage.getObjects(GeofenceSession.class, new Request(
            new Columns.Include("id"),
            new Condition.Compare("exitTime", "IS", "exitTime", null)
        ));
        
        return new MigrationStats(enterEvents.size(), exitEvents.size(), totalSessions.size(), openSessions.size());
    }
    
    /**
     * Statistics class for migration information
     */
    public static class MigrationStats {
        private final int enterEvents;
        private final int exitEvents;
        private final int totalSessions;
        private final int openSessions;
        
        public MigrationStats(int enterEvents, int exitEvents, int totalSessions, int openSessions) {
            this.enterEvents = enterEvents;
            this.exitEvents = exitEvents;
            this.totalSessions = totalSessions;
            this.openSessions = openSessions;
        }
        
        public int getEnterEvents() { return enterEvents; }
        public int getExitEvents() { return exitEvents; }
        public int getTotalSessions() { return totalSessions; }
        public int getOpenSessions() { return openSessions; }
        public int getClosedSessions() { return totalSessions - openSessions; }
        
        @Override
        public String toString() {
            return String.format(
                "Migration Stats: Enter Events=%d, Exit Events=%d, Total Sessions=%d, Open Sessions=%d, Closed Sessions=%d",
                enterEvents, exitEvents, totalSessions, openSessions, getClosedSessions()
            );
        }
    }
}
