package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.redhat.rhjmc.containerjfr.core.EventOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

abstract class AbstractRecordingCommand extends AbstractConnectedCommand {

    private static final Pattern ALL_EVENTS_PATTERN = Pattern.compile("^ALL$", Pattern.MULTILINE);
    private static final Pattern EVENTS_PATTERN = Pattern.compile("([\\w\\.\\$]+):([\\w]+)=([\\w\\d\\.]+)");

    protected final ClientWriter cw;
    protected final Function<JFRConnection, EventOptionsCustomizer> eventOptionsCustomizerSupplier;

    protected AbstractRecordingCommand(ClientWriter cw, Function<JFRConnection, EventOptionsCustomizer> eventOptionsCustomizerSupplier) {
        this.cw = cw;
        this.eventOptionsCustomizerSupplier = eventOptionsCustomizerSupplier;
    }

    protected IConstrainedMap<EventOptionID> enableEvents(String events) throws Exception {
        if (ALL_EVENTS_PATTERN.matcher(events).matches()) {
            return enableAllEvents();
        }

        return enableSelectedEvents(events);
    }

    protected IConstrainedMap<EventOptionID> enableAllEvents() throws Exception {
        EventOptionsCustomizer customizer = eventOptionsCustomizerSupplier.apply(connection);

        for (IEventTypeInfo eventTypeInfo : getService().getAvailableEventTypes()) {
            customizer.set(eventTypeInfo.getEventTypeID().getFullKey(), "enabled", "true");
        }

        return customizer.asMap();
    }

    protected IConstrainedMap<EventOptionID> enableSelectedEvents(String events) throws Exception {
        EventOptionsCustomizer customizer = eventOptionsCustomizerSupplier.apply(connection);

        Matcher matcher = EVENTS_PATTERN.matcher(events);
        while (matcher.find()) {
            String eventTypeId = matcher.group(1);
            String option = matcher.group(2);
            String value = matcher.group(3);

            customizer.set(eventTypeId, option, value);
        }

        return customizer.asMap();
    }

    protected boolean validateEvents(String events) {
        // TODO better validation of entire events string (not just looking for one acceptable setting)
        if (!ALL_EVENTS_PATTERN.matcher(events).matches() && !EVENTS_PATTERN.matcher(events).find()) {
            cw.println(String.format("%s is an invalid events pattern", events));
            return false;
        }

        return true;
    }
}
