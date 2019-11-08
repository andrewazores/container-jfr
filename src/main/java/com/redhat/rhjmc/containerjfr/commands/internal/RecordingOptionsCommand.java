package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.RecordingOptionsCustomizer;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@Singleton
class RecordingOptionsCommand extends AbstractConnectedCommand implements SerializableCommand {

    private static final Pattern OPTIONS_PATTERN = Pattern.compile("^([\\w]+)=([\\w\\.-_]+)$", Pattern.MULTILINE);
    private static final Pattern UNSET_PATTERN = Pattern.compile("^-([\\w]+)$", Pattern.MULTILINE);

    private final ClientWriter cw;
    private final Supplier<RecordingOptionsCustomizer> supplier;

    @Inject RecordingOptionsCommand(ClientWriter cw) {
        this.cw = cw;
        this.supplier = () -> new RecordingOptionsCustomizer(connection);
    }

    RecordingOptionsCommand(ClientWriter cw, Supplier<RecordingOptionsCustomizer> supplier) {
        this.cw = cw;
        this.supplier = supplier;
    }

    @Override
    public String getName() {
        return "recording-option";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String options = args[0];
        RecordingOptionsCustomizer customizer = supplier.get();

        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
        if (optionsMatcher.find()) {
            String option = optionsMatcher.group(1);
            String value = optionsMatcher.group(2);
            RecordingOptionsCustomizer.OptionKey.fromOptionName(option).ifPresent(k -> customizer.set(k, value));
            return;
        }

        Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
        unsetMatcher.find();
        RecordingOptionsCustomizer.OptionKey.fromOptionName(unsetMatcher.group(1)).ifPresent(customizer::unset);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        try {
            execute(args);
            return new SuccessOutput();
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 1) {
            cw.println("Expected one argument: recording option name");
            return false;
        }
        String options = args[0];

        Matcher optionsMatcher = OPTIONS_PATTERN.matcher(options);
        boolean optionsMatch = optionsMatcher.find();
        Matcher unsetMatcher = UNSET_PATTERN.matcher(options);
        boolean unsetMatch = unsetMatcher.find();
        if (!optionsMatch && !unsetMatch) {
            cw.println(String.format("%s is an invalid option string", options));
            return false;
        }

        String option = (optionsMatch ? optionsMatcher : unsetMatcher).group(1);
        boolean recognizedOption = RecordingOptionsCustomizer.OptionKey.fromOptionName(option)
            .isPresent();
        if (!recognizedOption) {
            cw.println(String.format("%s is an unrecognized or unsupported option", option));
            return false;
        }

        return true;
    }

}
