package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.EventOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest extends TestBase {

    AbstractRecordingCommand command;
    @Mock JFRConnection connection;
    @Mock EventOptionsCustomizer customizer;

    @BeforeEach
    void setup() {
        command = new BaseRecordingCommand(mockClientWriter, unused -> customizer);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "jdk:bar:baz",
        "jdk.Event",
        "Event",
    })
    void shouldNotValidateInvalidEventString(String events) {
        assertFalse(command.validateEvents(events));
        assertThat(stdout(), equalTo(events + " is an invalid events pattern\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "foo.Event:prop=val",
        "foo.Event:prop=val,bar.Event:thing=1",
        "foo.class$Inner:prop=val",
        "ALL"
    })
    void shouldValidateValidEventString(String events) {
        assertTrue(command.validateEvents(events));
        assertThat(stdout(), emptyString());
    }

    @Test
    void shouldBuildSelectedEventMap() throws Exception {
        command.enableEvents("foo.Bar$Inner:prop=some,bar.Baz$Inner2:key=val,jdk.CPULoad:enabled=true");

        verify(customizer).set("foo.Bar$Inner", "prop", "some");
        verify(customizer).set("bar.Baz$Inner2", "key", "val");
        verify(customizer).set("jdk.CPULoad", "enabled", "true");
        verify(customizer).asMap();

        verifyNoMoreInteractions(customizer);
    }

    @Test
    void shouldBuildAllEventMap() throws Exception {
        IEventTypeInfo mockEvent = mock(IEventTypeInfo.class);
        IEventTypeID mockEventTypeId = mock(IEventTypeID.class);
        when(mockEventTypeId.getFullKey()).thenReturn("com.example.Event");
        when(mockEvent.getEventTypeID()).thenReturn(mockEventTypeId);
        IFlightRecorderService mockService = mock(IFlightRecorderService.class);
        when(connection.getService()).thenReturn(mockService);
        when(mockService.getAvailableEventTypes()).thenReturn((Collection) Collections.singletonList(mockEvent));

        command.connectionChanged(connection);
        command.enableEvents("ALL");

        verify(customizer).set("com.example.Event", "enabled", "true");
        verify(customizer).asMap();

        verifyNoMoreInteractions(customizer);
    }

    static class BaseRecordingCommand extends AbstractRecordingCommand {
        BaseRecordingCommand(ClientWriter cw, Function<JFRConnection, EventOptionsCustomizer> eventOptionsCustomizerSupplier) {
            super(cw, eventOptionsCustomizerSupplier);
        }

        @Override
        public String getName() {
            return "base";
        }

        @Override
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public void execute(String[] args) { }
    }
}
