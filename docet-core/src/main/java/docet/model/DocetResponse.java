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
package docet.model;

/**
 * This is a data type class representing a generic results returned from
 * docet.
 *
 * @author matteo.casadei
 *
 */
public class DocetResponse {

    public static final int STATUS_CODE_OK = 0;
    public static final int STATUS_CODE_FAILURE = 1;

    private final int status;
    private final String errorCode;
    private final String errorMessage;

    public DocetResponse() {
        this(STATUS_CODE_OK, "", "");
    }

    public DocetResponse(final int status, final String errorCode, final String errorMessage) {
        this.errorCode = errorCode;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
