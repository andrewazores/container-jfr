package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.EventOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.RecordingOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@ExtendWith(MockitoExtension.class)
class DumpCommandTest {

    DumpCommand command;
    @Mock ClientWriter cw;
    @Mock WebServer exporter;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock EventOptionsCustomizer eventOptionsCustomizer;
    @Mock RecordingOptionsCustomizer recordingOptionsCustomizer;

    @BeforeEach
    void setup() {
        command = new DumpCommand(cw, exporter, unused -> eventOptionsCustomizer, () -> recordingOptionsCustomizer);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDump() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("dump"));
    }

    @Test
    void shouldPrintArgMessageWhenArgcInvalid() {
        assertFalse(command.validate(new String[0]));
        verify(cw).println("Expected three arguments: recording name, recording length, and event types");
    }

    @Test
    void shouldPrintMessageWhenRecordingNameInvalid() {
        assertFalse(command.validate(new String[]{".", "30", "foo.Bar:enabled=true"}));
        verify(cw).println(". is an invalid recording name");
    }

    @Test
    void shouldPrintMessageWhenRecordingLengthInvalid() {
        assertFalse(command.validate(new String[]{"recording", "nine", "foo.Bar:enabled=true"}));
        verify(cw).println("nine is an invalid recording length");
    }

    @Test
    void shouldPrintMessageWhenEventStringInvalid() {
        assertFalse(command.validate(new String[]{"recording", "30", "foo.Bar:=true"}));
        verify(cw).println("foo.Bar:=true is an invalid events pattern");
    }

    @Test
    void shouldValidateCorrectArgs() {
        assertTrue(command.validate(new String[]{"recording", "30", "foo.Bar:enabled=true"}));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldDumpRecordingOnExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        when(recordingOptionsCustomizer.set(Mockito.any(), Mockito.anyString())).thenReturn(recordingOptionsCustomizer);
        when(recordingOptionsCustomizer.asMap()).thenReturn(recordingOptions);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(eventOptionsCustomizer.asMap()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });

        ArgumentCaptor<RecordingOptionsCustomizer.OptionKey> keyCaptor = ArgumentCaptor.forClass(RecordingOptionsCustomizer.OptionKey.class);
        ArgumentCaptor<String> settingCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(recordingOptionsCustomizer, Mockito.times(2)).set(keyCaptor.capture(), settingCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());
        verify(exporter).addRecording(descriptorCaptor.capture());

        RecordingOptionsCustomizer.OptionKey nameKey = keyCaptor.getAllValues().get(0);
        RecordingOptionsCustomizer.OptionKey durationKey = keyCaptor.getAllValues().get(1);
        String actualName = settingCaptor.getAllValues().get(0);
        String actualDuration = settingCaptor.getAllValues().get(1);
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();
        IRecordingDescriptor recordingDescriptor = descriptorCaptor.getValue();

        MatcherAssert.assertThat(nameKey, Matchers.is(RecordingOptionsCustomizer.OptionKey.NAME));
        MatcherAssert.assertThat(durationKey, Matchers.is(RecordingOptionsCustomizer.OptionKey.DURATION));
        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(actualDuration, Matchers.equalTo("30s"));
        MatcherAssert.assertThat(recordingDescriptor, Matchers.sameInstance(descriptor));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventOptionsCustomizer).set(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(eventOptionsCustomizer).asMap();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(),Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldDumpRecordingOnSerializableExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());
        IConstrainedMap<String> recordingOptions = mock(IConstrainedMap.class);
        when(recordingOptionsCustomizer.set(Mockito.any(), Mockito.anyString())).thenReturn(recordingOptionsCustomizer);
        when(recordingOptionsCustomizer.asMap()).thenReturn(recordingOptions);
        IConstrainedMap<EventOptionID> events = mock(IConstrainedMap.class);
        when(eventOptionsCustomizer.asMap()).thenReturn(events);

        IRecordingDescriptor descriptor = mock(IRecordingDescriptor.class);
        when(service.start(Mockito.any(), Mockito.any())).thenReturn(descriptor);

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        SerializableCommand.Output out = command.serializableExecute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        ArgumentCaptor<RecordingOptionsCustomizer.OptionKey> keyCaptor = ArgumentCaptor.forClass(RecordingOptionsCustomizer.OptionKey.class);
        ArgumentCaptor<String> settingCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IConstrainedMap<EventOptionID>> eventsCaptor = ArgumentCaptor.forClass(IConstrainedMap.class);
        ArgumentCaptor<IRecordingDescriptor> descriptorCaptor = ArgumentCaptor.forClass(IRecordingDescriptor.class);
        verify(recordingOptionsCustomizer, Mockito.times(2)).set(keyCaptor.capture(), settingCaptor.capture());
        verify(service).getAvailableRecordings();
        verify(service).start(recordingOptionsCaptor.capture(), eventsCaptor.capture());
        verify(exporter).addRecording(descriptorCaptor.capture());

        RecordingOptionsCustomizer.OptionKey nameKey = keyCaptor.getAllValues().get(0);
        RecordingOptionsCustomizer.OptionKey durationKey = keyCaptor.getAllValues().get(1);
        String actualName = settingCaptor.getAllValues().get(0);
        String actualDuration = settingCaptor.getAllValues().get(1);
        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        IConstrainedMap<EventOptionID> actualEvents = eventsCaptor.getValue();
        IRecordingDescriptor recordingDescriptor = descriptorCaptor.getValue();

        MatcherAssert.assertThat(nameKey, Matchers.is(RecordingOptionsCustomizer.OptionKey.NAME));
        MatcherAssert.assertThat(durationKey, Matchers.is(RecordingOptionsCustomizer.OptionKey.DURATION));
        MatcherAssert.assertThat(actualName, Matchers.equalTo("foo"));
        MatcherAssert.assertThat(actualDuration, Matchers.equalTo("30s"));
        MatcherAssert.assertThat(recordingDescriptor, Matchers.sameInstance(descriptor));
        MatcherAssert.assertThat(actualEvents, Matchers.sameInstance(events));
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));
        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(eventOptionsCustomizer).set(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(eventOptionsCustomizer).asMap();

        MatcherAssert.assertThat(eventCaptor.getValue(), Matchers.equalTo("foo.Bar"));
        MatcherAssert.assertThat(optionCaptor.getValue(),Matchers.equalTo("enabled"));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("true"));

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleNameCollisionOnExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        command.execute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });

        verify(service).getAvailableRecordings();
        verify(cw).println("Recording with name \"foo\" already exists");

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleNameCollisionOnSerializableExecute() throws Exception {
        IRecordingDescriptor existingRecording = mock(IRecordingDescriptor.class);
        when(existingRecording.getName()).thenReturn("foo");

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Arrays.asList(existingRecording));

        verifyZeroInteractions(connection);
        verifyZeroInteractions(service);
        verifyZeroInteractions(exporter);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.FailureOutput) out).getPayload(), Matchers.equalTo("Recording with name \"foo\" already exists"));

        verify(service).getAvailableRecordings();

        verifyNoMoreInteractions(connection);
        verifyNoMoreInteractions(service);
        verifyNoMoreInteractions(exporter);
    }

    @Test
    void shouldHandleExceptionOnSerializableExecute() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableRecordings()).thenReturn(Collections.emptyList());

        SerializableCommand.Output<?> out = command.serializableExecute(new String[]{ "foo", "30", "foo.Bar:enabled=true" });
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(((SerializableCommand.ExceptionOutput) out).getPayload(), Matchers.equalTo("NullPointerException: "));
    }

}
