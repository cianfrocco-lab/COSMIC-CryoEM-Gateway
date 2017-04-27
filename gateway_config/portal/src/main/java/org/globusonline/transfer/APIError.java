/*
 * Copyright 2010 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.globusonline.transfer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class APIError extends Exception {
    public int statusCode;
    public String statusMessage;

    public String category;

    public String resource;
    public String requestId;
    public String code;
    public String message;

    public static final String CLIENT_ERROR = "ClientError";
    public static final String SERVER_ERROR = "ServerError";
    public static final String SERVICE_UNAVAILABLE = "ServiceUnavailable";
    public static final String EXTERNAL_ERROR = "ExternalError";
    public static final String UNKNOWN_ERROR = "UnknownError";

    public APIError(int statusCode, String statusMessage, String code) {
        this(statusCode, statusMessage, code, null, null, null);
    }

    public APIError(int statusCode, String statusMessage, String code,
                    String resource, String requestId, String message) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;

        // Error category is the first part of the error code.
        if (code != null && code.length() > 0) {
            String[] codeParts = code.split(".", 2);
            this.category = codeParts[0];
        } else {
            this.category = "ServerError";
            code = "ServerError.UnknownError";
        }

        this.resource = resource;
        this.requestId = requestId;
        this.code = code;
        this.message = message;
    }

    public String toString() {
        return code + "(" + statusCode + " "
               + statusMessage + ") on request '" + requestId
               + "' resource '" + resource + "': " + message;
    }
}
