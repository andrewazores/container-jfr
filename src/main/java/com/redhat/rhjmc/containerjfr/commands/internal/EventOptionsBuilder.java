package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.HashMap;
import java.util.Map;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

class EventOptionsBuilder {

    private final JFRConnection connection;
    private final IMutableConstrainedMap<EventOptionID> map;
    private Map<IEventTypeID, Map<String, IOptionDescriptor<?>>> knownTypes;
    private Map<String, IEventTypeID> eventIds;

    // Testing-only constructor
    EventOptionsBuilder(ClientWriter cw, JFRConnection connection) throws FlightRecorderException {
        this.connection = connection;
        this.map = connection.getService().getDefaultEventOptions().emptyWithSameConstraints();
        knownTypes = new HashMap<>();
        eventIds = new HashMap<>();

        if (connection.isV1()) {
            cw.println("Flight Recorder V1 is not yet supported");
            return;
        }

        for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
            eventIds.put(eventTypeInfo.getEventTypeID().getFullKey(), eventTypeInfo.getEventTypeID());
            knownTypes.putIfAbsent(eventTypeInfo.getEventTypeID(), new HashMap<>(eventTypeInfo.getOptionDescriptors()));
        }
    }

    EventOptionsBuilder addEvent(String typeId, String option, String value) throws Exception {
        if (!eventIds.containsKey(typeId)) {
            throw new EventTypeException(typeId);
        }
        Map<String, IOptionDescriptor<?>> optionDescriptors = knownTypes.get(eventIds.get(typeId));
        if (!optionDescriptors.containsKey(option)) {
            throw new EventOptionException(typeId, option);
        }
        IConstraint<?> constraint = optionDescriptors.get(option).getConstraint();
        Object parsedValue = constraint.parseInteractive(value);
        constraint.validate(capture(parsedValue));
        this.map.put(new EventOptionID(eventIds.get(typeId), option), parsedValue);

        return this;
    }

    static <T, V> V capture(T t) {
        // TODO clean up this generics hack
        return (V) t;
    }

    IConstrainedMap<EventOptionID> build() {
        if (connection.isV1()) {
            return null;
        }
        return map;
    }

    @SuppressWarnings("serial")
    static class EventTypeException extends Exception {
        EventTypeException(String eventType) {
            super(String.format("Unknown event type \"%s\"", eventType));
        }
    }

    @SuppressWarnings("serial")
    static class EventOptionException extends Exception {
        EventOptionException(String eventType, String option) {
            super(String.format("Unknown option \"%s\" for event \"%s\"", option, eventType));
        }
    }

    public static class Factory {
        private final ClientWriter cw;

        public Factory(ClientWriter cw) {
            this.cw = cw;
        }

        public EventOptionsBuilder create(JFRConnection connection) throws FlightRecorderException {
            return new EventOptionsBuilder(cw, connection);
        }
    }
}
