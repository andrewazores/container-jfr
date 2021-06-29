/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.rules;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.script.Bindings;

import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import delight.nashornsandbox.NashornSandbox;
import delight.nashornsandbox.NashornSandboxes;

class RuleMatcher {

    private final NashornSandbox sandbox;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();

    RuleMatcher() {
        this.sandbox = NashornSandboxes.create();
        this.sandbox.setExecutor(executor);
        this.sandbox.setMaxCPUTime(250);
        this.sandbox.allowNoBraces(true);
        this.sandbox.allowExitFunctions(false);
        this.sandbox.allowLoadFunctions(false);
        this.sandbox.allowReadFunctions(false);
        this.sandbox.allowGlobalsObjects(false);
        this.sandbox.allowPrintFunctions(true);
        this.sandbox.setMaxPreparedStatements(50);
    }

    public boolean applies(Rule rule, ServiceRef serviceRef) {
        Bindings bindings = this.sandbox.createBindings();
        // FIXME don't use Gson for this, just directly convert the ServiceRef to a Map
        bindings.put("target", gson.fromJson(gson.toJson(serviceRef), Map.class));
        try {
            Object result = this.sandbox.eval(rule.getMatchExpression(), bindings);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                throw new IllegalArgumentException(
                        String.format("Non-boolean rule expression evaluation result: %s", result));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
