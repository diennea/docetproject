/*
 * Licensed to Diennea S.r.l. under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Diennea S.r.l. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package docet;

import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

/**
 * Represents Docet's execution context (at the request level).
 *
 */
public class DocetExecutionContext {

    private final HttpServletRequest executionRequest;
    private final Set<String> permittedPackages;

    public HttpServletRequest getExecutionRequest() {
        return executionRequest;
    }

    public DocetExecutionContext(final HttpServletRequest req) {
        this.executionRequest = req;
        this.permittedPackages = new HashSet<>();
    }

    public void setAccessPermission(final String packageid, final AccessPermission access) {
        switch (access) {
            case ALLOW:
                this.permittedPackages.add(packageid);
                break;
            case DISALLOW:
                this.permittedPackages.remove(packageid);
                break;
            default: new RuntimeException("Value " + access + " is not supported");
        }
    }

    public boolean checkAccessPermission(final String packageid, final AccessPermission access) {
        boolean res = false;
        switch (access) {
            case ALLOW:
                res = this.permittedPackages.contains(packageid);
                break;
            case DISALLOW:
                res = !this.permittedPackages.contains(packageid);
                break;
            default: new RuntimeException("Access level " + access + " is not supported");
        }
        return res;
    }

    public static enum AccessPermission {
        ALLOW,
        DISALLOW
    }
}
