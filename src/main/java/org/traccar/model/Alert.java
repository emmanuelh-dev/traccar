/*
 * Copyright 2016 - 2020 Anton Tananaev (anton@traccar.org)
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
package org.traccar.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.traccar.storage.QueryIgnore;
import org.traccar.storage.StorageName;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@StorageName("tc_alerts")
public class Alert extends Message {

    public Alert(Event event, User user, Notification notification) {
        setEventId(event.getId());
        setAttributes(event.getAttributes());
        setType(event.getType());
        setDeviceId(event.getDeviceId());
        setUserId(user.getId());
        setNotificationId(notification.getId());
        setAlertTime(event.getEventTime());
    }

    public Alert() {
    }

    private Date alertTime;

    public Date getAlertTime() {
        return alertTime;
    }

    public void setAlertTime(Date alertTime) {
        this.alertTime = alertTime;
    }

    private long userId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    private long eventId;

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    private long notificationId;

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    @JsonIgnore
    @QueryIgnore
    public Set<String> getNotificatorsTypes() {
        final Set<String> result = new HashSet<>();
        String notificators = this.getString("notificators");
        if (notificators != null) {
            final String[] transportsList = notificators.split(",");
            for (String transport : transportsList) {
                result.add(transport.trim());
            }
        }
        return result;
    }

    @JsonIgnore
    @QueryIgnore
    public void addNotificator(String transport) {
        String notificators = this.getString("notificators");
        if (notificators != null) {
            notificators = notificators.concat(",");
            notificators = notificators.concat(transport);
        } else {
            notificators = transport;
        }
        this.set("notificators", notificators);
    }

}
