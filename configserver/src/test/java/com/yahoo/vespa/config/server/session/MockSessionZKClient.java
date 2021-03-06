// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.config.provision.ProvisionInfo;
import com.yahoo.transaction.Transaction;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;

import java.util.Optional;

/**
 * Overrides application package fetching, because this part is hard to do without feeding a full app.
 *
 * @author lulf
 * @since 5.1
 */
public class MockSessionZKClient extends SessionZooKeeperClient {

    private ApplicationPackage app = null;
    private Optional<ProvisionInfo> info = null;
    private Session.Status sessionStatus;

    public MockSessionZKClient(Curator curator, Path rootPath) {
        this(curator, rootPath, (ApplicationPackage)null);
    }

    public MockSessionZKClient(Curator curator, Path rootPath, Optional<ProvisionInfo> provisionInfo) {
        this(curator, rootPath);
        this.info = provisionInfo;
    }

    public MockSessionZKClient(Curator curator, Path rootPath, ApplicationPackage application) {
        super(curator, rootPath);
        this.app = application;
    }

    public MockSessionZKClient(ApplicationPackage app) {
        super(new MockCurator(), Path.createRoot());
        this.app = app;
    }

    @Override
    public ApplicationPackage loadApplicationPackage() {
        if (app != null) return app;
        return new MockApplicationPackage.Builder().withEmptyServices().build();
    }

    @Override
    ProvisionInfo getProvisionInfo() {
        return info.orElseThrow(() -> new IllegalStateException("Trying to read provision info, but no provision info exists"));
    }

    @Override
    public Transaction createWriteStatusTransaction(Session.Status status) {
        return new DummyTransaction().add((DummyTransaction.RunnableOperation) () -> {
            sessionStatus = status;
        });
    }

    @Override
    public Session.Status readStatus() {
        return sessionStatus;
    }
}
