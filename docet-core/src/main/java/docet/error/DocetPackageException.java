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

public class DocetPackageException extends Exception {

    private static final long serialVersionUID = 2022l;

    public static final String MSG_NOT_FOUND = "package_not_found";
    public static final String MSG_ACCESS_DENIED = "package_access_denied";
    public static final String MSG_DESCRIPTION_ERROR = "package_description_error";

    private DocetPackageException(String message) {
        super(message);
    }
    
    private DocetPackageException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DocetPackageException buildPackageAccessDeniedException() {
        return new DocetPackageException(MSG_ACCESS_DENIED);
    }

    public static DocetPackageException buildPackageNotFoundException(Throwable cause) {
        return new DocetPackageException(MSG_NOT_FOUND, cause);
    }

    public static DocetPackageException buildPackageDescriptionException(Throwable cause) {
        return new DocetPackageException(MSG_DESCRIPTION_ERROR, cause);
    }
}
