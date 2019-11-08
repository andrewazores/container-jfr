package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.RecordingOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

import org.openjdk.jmc.common.unit.IConstrainedMap;

@Singleton
class StartRecordingCommand extends AbstractRecordingCommand implements SerializableCommand {

    private final WebServer exporter;
    private final Supplier<RecordingOptionsCustomizer> recordingOptionsCustomizerSupplier;

    @Inject
    StartRecordingCommand(ClientWriter cw, WebServer exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory) {
        super(cw, eventOptionsBuilderFactory);
        this.exporter = exporter;
        this.recordingOptionsCustomizerSupplier = () -> new RecordingOptionsCustomizer(connection);
    }

    // testing-only constructor
    StartRecordingCommand(ClientWriter cw, WebServer exporter, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            Supplier<RecordingOptionsCustomizer> recordingOptionsCustomizerSupplier) {
        super(cw, eventOptionsBuilderFactory);
        this.exporter = exporter;
        this.recordingOptionsCustomizerSupplier = recordingOptionsCustomizerSupplier;
    }

    @Override
    public String getName() {
        return "start";
    }

    /**
     * Two args expected.
     * First argument is recording name, second argument is recording length in seconds.
     * Second argument is comma-separated event options list, ex. jdk.SocketWrite:enabled=true,com.foo:ratio=95.2
     */
    @Override
    public void execute(String[] args) throws Exception {
        String name = args[0];
        String events = args[1];

        if (getDescriptorByName(name).isPresent()) {
            cw.println(String.format("Recording with name \"%s\" already exists", name));
            return;
        }

        IConstrainedMap<String> recordingOptions = recordingOptionsCustomizerSupplier.get()
            .set(RecordingOptionsCustomizer.OptionKey.NAME, name)
            .asMap();
        this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            String name = args[0];
            String events = args[1];

            if (getDescriptorByName(name).isPresent()) {
                return new FailureOutput(String.format("Recording with name \"%s\" already exists", name));
            }

            IConstrainedMap<String> recordingOptions = recordingOptionsCustomizerSupplier.get()
                .set(RecordingOptionsCustomizer.OptionKey.NAME, name)
                .asMap();
            this.exporter.addRecording(getService().start(recordingOptions, enableEvents(events)));
            return new StringOutput(this.exporter.getDownloadURL(name));
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 2) {
            cw.println("Expected two arguments: recording name and event types");
            return false;
        }

        String name = args[0];
        String events = args[1];

        if (!validateRecordingName(name)) {
            cw.println(String.format("%s is an invalid recording name", name));
            return false;
        }

        return validateEvents(events);
    }
}
