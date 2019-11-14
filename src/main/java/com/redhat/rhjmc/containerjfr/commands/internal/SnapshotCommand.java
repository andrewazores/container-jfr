package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.function.Function;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.EventOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.RecordingOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.jmc.CopyRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

@Singleton
class SnapshotCommand extends AbstractRecordingCommand implements SerializableCommand {

    private final WebServer exporter;
    private final Supplier<RecordingOptionsCustomizer> recordingOptionsCustomizerSupplier;

    @Inject
    SnapshotCommand(ClientWriter cw, WebServer exporter) {
        super(cw, EventOptionsCustomizer::new);
        this.exporter = exporter;
        this.recordingOptionsCustomizerSupplier = () -> new RecordingOptionsCustomizer(connection);
    }

    // testing-only constructor
    SnapshotCommand(ClientWriter cw, WebServer exporter, Function<JFRConnection, EventOptionsCustomizer> eventOptionsCustomizerSupplier, Supplier<RecordingOptionsCustomizer> recordingOptionsCustomizerSupplier) {
        super(cw, eventOptionsCustomizerSupplier);
        this.exporter = exporter;
        this.recordingOptionsCustomizerSupplier = recordingOptionsCustomizerSupplier;
    }

    @Override
    public String getName() {
        return "snapshot";
    }

    @Override
    public void execute(String[] args) throws Exception {
        IRecordingDescriptor descriptor = getService().getSnapshotRecording();

        String rename = String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());
        cw.println(String.format("Latest snapshot: \"%s\"", rename));

        IConstrainedMap<String> recordingOptions = recordingOptionsCustomizerSupplier.get()
            .set(RecordingOptionsCustomizer.OptionKey.NAME, rename)
            .asMap();
        getService().updateRecordingOptions(descriptor, recordingOptions);
        exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            IRecordingDescriptor descriptor = getService().getSnapshotRecording();

            String rename = String.format("%s-%d", descriptor.getName().toLowerCase(), descriptor.getId());

            IConstrainedMap<String> recordingOptions = recordingOptionsCustomizerSupplier.get()
                .set(RecordingOptionsCustomizer.OptionKey.NAME, rename)
                .asMap();
            getService().updateRecordingOptions(descriptor, recordingOptions);
            exporter.addRecording(new RenamedSnapshotDescriptor(rename, descriptor));

            return new StringOutput(rename);
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 0) {
            cw.println("No arguments expected");
            return false;
        }
        return true;
    }

    private static class RenamedSnapshotDescriptor extends CopyRecordingDescriptor {
        private final String rename;

        RenamedSnapshotDescriptor(String rename, IRecordingDescriptor original) {
            super(original);
            this.rename = rename;
        }

        @Override
        public String getName() {
            return rename;
        }
    }

}
