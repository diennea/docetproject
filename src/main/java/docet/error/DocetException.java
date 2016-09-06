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
package docet.error;

public class DocetException extends Exception {

    public static final String CODE_GENERIC_ERROR = "generic";
    public static final String CODE_PACKAGE_NOTFOUND = "package_not_found";
    public static final String CODE_RESOURCE_NOTFOUND = "resource_not_found";
    public static final String CODE_PACKAGE_NOTAVAILABLE = "package_not_available";

    private final String code;

    public DocetException(final String code, final String message) {
        super(message);
        this.code = code;
    }
    
    public DocetException(final String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
