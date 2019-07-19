package com.redhat.rhjmc.containerjfr.sys;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

@Module
public abstract class SystemModule {
    @Provides
    @Singleton
    static Clock provideClock() {
        return new Clock();
    }

    @Provides
    @Singleton
    static Environment provideEnvironment() {
        return new Environment();
    }

    @Provides
    @Singleton
    static FileSystem provideFileSystem() {
        return new FileSystem();
    }
}