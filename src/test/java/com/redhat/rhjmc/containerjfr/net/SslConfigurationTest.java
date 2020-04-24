/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;

import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;

@ExtendWith(MockitoExtension.class)
class SslConfigurationTest {
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock SslConfiguration.SslConfigurationStrategy strategy;

    SslConfiguration sslConf;

    @BeforeEach
    void setup() {
        sslConf = new SslConfiguration(env, fs, strategy);
    }

    @Test
    void shouldFallbackToPlainHttp() throws SslConfiguration.SslConfigurationException {
        Path dne = mock(Path.class);
        when(env.hasEnv(anyString())).thenReturn(false);
        when(fs.pathOf(anyString())).thenReturn(dne);
        when(dne.resolve(anyString())).thenReturn(dne);
        when(fs.exists(any(Path.class))).thenReturn(false);

        sslConf = new SslConfiguration(env, fs);

        MatcherAssert.assertThat(sslConf.enabled(), Matchers.equalTo(false));
    }

    @Test
    void shouldObtainKeyStorePathIfSpecified() throws SslConfiguration.SslConfigurationException {
        Path path = mock(Path.class);

        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(true);
        when(env.getEnv("KEYSTORE_PATH")).thenReturn("foobar");
        when(fs.pathOf("foobar")).thenReturn(path);
        when(path.normalize()).thenReturn(path);
        when(fs.exists(path)).thenReturn(true);
        MatcherAssert.assertThat(sslConf.obtainKeyStorePathIfSpecified(), Matchers.equalTo(path));
    }

    @Test
    void shouldReturnNullIfKeyStorePathUnspecified()
            throws SslConfiguration.SslConfigurationException {
        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(false);

        MatcherAssert.assertThat(sslConf.obtainKeyStorePathIfSpecified(), Matchers.equalTo(null));
        verifyNoMoreInteractions(env);
        verifyNoMoreInteractions(fs);
    }

    @Test
    void shouldThrowIfKeyStorePathRefersToANonExistentFile() {
        Path dne = mock(Path.class);

        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(true);
        when(env.getEnv("KEYSTORE_PATH")).thenReturn("foobar");
        when(fs.pathOf("foobar")).thenReturn(dne);
        when(dne.normalize()).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> sslConf.obtainKeyStorePathIfSpecified());
    }

    @Test
    void shouldDiscoverJksKeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        when(home.resolve("container-jfr-keystore.jks")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);

        MatcherAssert.assertThat(
                sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldDiscoverPfxKeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);
        Path dne = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        when(home.resolve("container-jfr-keystore.pfx")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);

        MatcherAssert.assertThat(
                sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldDiscoverP12KeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);
        Path dne = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        when(home.resolve("container-jfr-keystore.p12")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);

        MatcherAssert.assertThat(
                sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldReturnNullIfNoKeyStoreInDefaultLocations() {
        Path home = mock(Path.class);
        Path dne = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        MatcherAssert.assertThat(
                sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(null));

        InOrder inOrder = inOrder(home);
        inOrder.verify(home).resolve("container-jfr-keystore.jks");
        inOrder.verify(home).resolve("container-jfr-keystore.pfx");
        inOrder.verify(home).resolve("container-jfr-keystore.p12");

        verify(fs, times(3)).exists(dne);
        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldObtainKeyCertPathPairIfSpecified()
            throws SslConfiguration.SslConfigurationException {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        Pair<Path, Path> pair = sslConf.obtainKeyCertPathPairIfSpecified();
        MatcherAssert.assertThat(pair.getLeft(), Matchers.equalTo(key));
        MatcherAssert.assertThat(pair.getRight(), Matchers.equalTo(cert));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldReturnNullIfKeyCertPathsUnspecified()
            throws SslConfiguration.SslConfigurationException {
        when(env.hasEnv("KEY_PATH")).thenReturn(false);
        when(env.hasEnv("CERT_PATH")).thenReturn(false);

        MatcherAssert.assertThat(
                sslConf.obtainKeyCertPathPairIfSpecified(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfKeyPathUnspecified() {
        when(env.hasEnv("KEY_PATH")).thenReturn(false);
        when(env.hasEnv("CERT_PATH")).thenReturn(true);

        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfCertPathUnspecified() {
        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.hasEnv("CERT_PATH")).thenReturn(false);

        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfKeyPathRefersToANonExistentFile() {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(false);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);

        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfCertPathRefersToANonExistentFile() {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(false);

        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldDiscoverKeyCertPathPairInDefaultLocations() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        Pair<Path, Path> pair = sslConf.discoverKeyCertPathPairInDefaultLocations();
        MatcherAssert.assertThat(pair.getLeft(), Matchers.equalTo(key));
        MatcherAssert.assertThat(pair.getRight(), Matchers.equalTo(cert));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldReturnNullIfKeyFileNotDiscovered() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(false);

        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        MatcherAssert.assertThat(
                sslConf.discoverKeyCertPathPairInDefaultLocations(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldReturnNullIfCertFileNotDiscovered() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(false);

        MatcherAssert.assertThat(
                sslConf.discoverKeyCertPathPairInDefaultLocations(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void testNoSslStrategyAppliesOptions() {
        HttpServerOptions options = mock(HttpServerOptions.class);
        when(options.setSsl(anyBoolean())).thenReturn(options);

        SslConfiguration.SslConfigurationStrategy noSslStrategy =
                new SslConfiguration.NoSslStrategy();
        MatcherAssert.assertThat(
                noSslStrategy.applyToHttpServerOptions(options), Matchers.equalTo(options));

        verify(options).setSsl(false);
        verifyNoMoreInteractions(options);
    }

    @Test
    void shouldNoSslStrategyReturnFalseForEnabled() {
        SslConfiguration.SslConfigurationStrategy noSslStrategy =
                new SslConfiguration.NoSslStrategy();
        MatcherAssert.assertThat(noSslStrategy.enabled(), Matchers.equalTo(false));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"key", "foo.abc", "bar.pem", "foo/bar.xyz"})
    void shouldKeyStoreStrategyThrowOnUnrecognizedTypes(String keyStore) {
        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> new SslConfiguration.KeyStoreStrategy(Path.of(keyStore), ""));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"key.jks", "foo.pfx", "bar.p12", "foo/bar.jks"})
    void shouldKeyStoreStrategyConstructsWithSupportedTypes(String keyStore)
            throws SslConfiguration.SslConfigurationException {
        new SslConfiguration.KeyStoreStrategy(Path.of(keyStore), "");
    }

    @Test
    void shouldKeyStoreStrategyThrowOnNullPassword() {
        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> new SslConfiguration.KeyStoreStrategy(Path.of("key.jks"), null));
    }

    @Test
    void testKeyStoreStrategyAppliesJksOptions() throws SslConfiguration.SslConfigurationException {
        SslConfiguration.SslConfigurationStrategy keyStoreStrategy =
                new SslConfiguration.KeyStoreStrategy(Path.of("key.jks"), "password");

        HttpServerOptions options = mock(HttpServerOptions.class);
        when(options.setSsl(anyBoolean())).thenReturn(options);
        when(options.setKeyStoreOptions(any()))
                .thenAnswer(
                        invocation -> {
                            JksOptions jksOptions = invocation.getArgument(0);
                            MatcherAssert.assertThat(
                                    jksOptions.getPath(), Matchers.equalTo("key.jks"));
                            MatcherAssert.assertThat(
                                    jksOptions.getPassword(), Matchers.equalTo("password"));
                            return null;
                        })
                .thenReturn(options);

        keyStoreStrategy.applyToHttpServerOptions(options);

        verify(options).setSsl(true);
        verify(options).setKeyStoreOptions(any());
        verifyNoMoreInteractions(options);
    }

    @ParameterizedTest
    @ValueSource(strings = {"key.pfx", "key.p12"})
    void testKeyStoreStrategyAppliesJksOptions(String keyStore)
            throws SslConfiguration.SslConfigurationException {
        SslConfiguration.SslConfigurationStrategy keyStoreStrategy =
                new SslConfiguration.KeyStoreStrategy(Path.of(keyStore), "password");

        HttpServerOptions options = mock(HttpServerOptions.class);
        when(options.setSsl(anyBoolean())).thenReturn(options);
        when(options.setPfxKeyCertOptions(any()))
                .thenAnswer(
                        invocation -> {
                            PfxOptions pfxOptions = invocation.getArgument(0);
                            MatcherAssert.assertThat(
                                    pfxOptions.getPath(), Matchers.equalTo(keyStore));
                            MatcherAssert.assertThat(
                                    pfxOptions.getPassword(), Matchers.equalTo("password"));
                            return null;
                        })
                .thenReturn(options);

        keyStoreStrategy.applyToHttpServerOptions(options);

        verify(options).setSsl(true);
        verify(options).setPfxKeyCertOptions(any());
        verifyNoMoreInteractions(options);
    }

    @Test
    void shouldKeyStoreStrategyReturnTrueForEnabled()
            throws SslConfiguration.SslConfigurationException {
        SslConfiguration.SslConfigurationStrategy noSslStrategy =
                new SslConfiguration.KeyStoreStrategy(Path.of("key.jks"), "");
        MatcherAssert.assertThat(noSslStrategy.enabled(), Matchers.equalTo(true));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"key.jks", "key.p12", "key.pfx", "foo.abc", "foobar"})
    void shouldKeyCertStrategyThrowOnUnrecognizedKeyTypes(String key) {
        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> new SslConfiguration.KeyCertStrategy(Path.of(key), Path.of("cert.pem")));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"key.jks", "key.p12", "key.pfx", "foo.abc", "foobar"})
    void shouldKeyCertStrategyThrowOnUnrecognizedCertTypes(String cert) {
        assertThrows(
                SslConfiguration.SslConfigurationException.class,
                () -> new SslConfiguration.KeyCertStrategy(Path.of("key.pem"), Path.of(cert)));
    }

    @Test
    void testKeyCertStrategyAppliesOptions() throws SslConfiguration.SslConfigurationException {
        SslConfiguration.SslConfigurationStrategy ketCertStrategy =
                new SslConfiguration.KeyCertStrategy(Path.of("key.pem"), Path.of("cert.pem"));

        HttpServerOptions options = mock(HttpServerOptions.class);
        when(options.setSsl(anyBoolean())).thenReturn(options);
        when(options.setPemKeyCertOptions(any()))
                .thenAnswer(
                        invocation -> {
                            PemKeyCertOptions keyCertOptions = invocation.getArgument(0);
                            MatcherAssert.assertThat(
                                    keyCertOptions.getKeyPath(), Matchers.equalTo("key.pem"));
                            MatcherAssert.assertThat(
                                    keyCertOptions.getCertPath(), Matchers.equalTo("cert.pem"));
                            return null;
                        })
                .thenReturn(options);

        ketCertStrategy.applyToHttpServerOptions(options);

        verify(options).setSsl(true);
        verify(options).setPemKeyCertOptions(any());
        verifyNoMoreInteractions(options);
    }

    @Test
    void shouldKeyCertStrategyReturnTrueForEnabled()
            throws SslConfiguration.SslConfigurationException {
        SslConfiguration.SslConfigurationStrategy noSslStrategy =
                new SslConfiguration.KeyCertStrategy(Path.of("key.pem"), Path.of("cert.pem"));
        MatcherAssert.assertThat(noSslStrategy.enabled(), Matchers.equalTo(true));
    }
}
