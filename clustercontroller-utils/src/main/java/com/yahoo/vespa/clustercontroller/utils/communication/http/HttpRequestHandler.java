// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

public interface HttpRequestHandler {

    public HttpResult handleRequest(HttpRequest request) throws Exception;
}
