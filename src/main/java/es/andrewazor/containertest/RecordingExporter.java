package es.andrewazor.containertest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class RecordingExporter {

    private final IFlightRecorderService service;
    private final ServerImpl server;
    private final Map<String, IRecordingDescriptor> recordings = new HashMap<>();
    private final Map<String, Integer> downloadCounts = new HashMap<>();

    RecordingExporter(IFlightRecorderService service) throws IOException {
        this.service = service;
        this.server = new ServerImpl();

        System.out.println(String.format("Recordings available at http://%s:%d/$RECORDING_NAME",
                InetAddress.getLocalHost().getHostAddress(), server.getListeningPort()));
    }

    public void addRecording(IRecordingDescriptor descriptor) {
        recordings.put(descriptor.getName(), descriptor);
    }

    public int getDownloadCount(String recordingName) {
        return this.downloadCounts.getOrDefault(recordingName, -1);
    }

    public String getDownloadURL(String recordingName) throws UnknownHostException {
        return String.format("http://%s:%d/%s", InetAddress.getLocalHost().getHostAddress(), server.getListeningPort(), recordingName);
    }

    void stop() {
        this.server.stop();
    }

    private class ServerImpl extends NanoHTTPD {
        private ServerImpl() throws IOException {
            super(8080);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String recordingName = session.getUri().substring(1);
            if (!recordings.containsKey(recordingName)) {
                return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s not found", recordingName));
            }
            try {
                downloadCounts.put(recordingName, downloadCounts.getOrDefault(recordingName, 0) + 1);
                return newChunkedResponse(Status.OK, "application/octet-stream",
                        service.openStream(recordings.get(recordingName), false));
            } catch (FlightRecorderException fre) {
                fre.printStackTrace();
                return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s could not be opened", recordingName));
            }
        }
    }
}