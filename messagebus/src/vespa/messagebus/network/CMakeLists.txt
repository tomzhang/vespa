# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(messagebus_network OBJECT
    SOURCES
    identity.cpp
    oosclient.cpp
    oosmanager.cpp
    rpcnetwork.cpp
    rpcnetworkparams.cpp
    rpcsendv1.cpp
    rpcservice.cpp
    rpcserviceaddress.cpp
    rpcservicepool.cpp
    rpctarget.cpp
    rpctargetpool.cpp
    DEPENDS
)
