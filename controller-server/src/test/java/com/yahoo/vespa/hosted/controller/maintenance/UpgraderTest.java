// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class UpgraderTest {

    @Test
    public void testUpgrading() {
        // --- Setup
        DeploymentTester tester = new DeploymentTester();
        tester.upgrader().maintain();
        assertEquals("No system version: Nothing to do", 0, tester.buildSystem().jobs().size());

        Version version = Version.fromString("5.0"); // (lower than the hardcoded version in the config server client)
        tester.updateVersionStatus(version);

        tester.upgrader().maintain();
        assertEquals("No applications: Nothing to do", 0, tester.buildSystem().jobs().size());

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0", 2, "default");
        Application default1 = tester.createAndDeploy("default1", 3, "default");
        Application default2 = tester.createAndDeploy("default2", 4, "default");
        Application conservative0 = tester.createAndDeploy("conservative0", 5, "conservative");

        tester.upgrader().maintain();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released - everything goes smoothly
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServerClientMock().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Canaries done: Should upgrade defaults", 3, tester.buildSystem().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Normals done: Should upgrade conservatives", 1, tester.buildSystem().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released - which fails a Canary
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgradeWithError(canary0, version, "canary", DeploymentJobs.JobType.stagingTest);
        assertEquals("Other Canary was cancelled", 2, tester.buildSystem().jobs().size());

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Version broken, but Canaries should keep trying", 2, tester.buildSystem().jobs().size());

        // --- A new version is released - which repairs the Canary app and fails a default
        version = Version.fromString("5.3");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServerClientMock().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();

        assertEquals("Canaries done: Should upgrade defaults", 3, tester.buildSystem().jobs().size());

        tester.completeUpgradeWithError(default0, version, "default", DeploymentJobs.JobType.stagingTest);
        tester.completeUpgrade(default1, version, "default");
        tester.completeUpgrade(default2, version, "default");

        tester.updateVersionStatus(version);
        assertEquals("Not enough evidence to mark this neither broken nor high",
                     VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Upgrade with error should retry", 1, tester.buildSystem().jobs().size());

        // --- Failing application is repaired by changing the application, causing confidence to move above 'high' threshold
        // Deploy application change
        tester.deployCompletely("default0");
        // Complete upgrade
        tester.upgrader().maintain();
        tester.completeUpgrade(default0, version, "default");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.high, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Normals done: Should upgrade conservatives", 1, tester.buildSystem().jobs().size());
        tester.completeUpgrade(conservative0, version, "conservative");

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("Nothing to do", 0, tester.buildSystem().jobs().size());
    }

    @Test
    public void testUpgradingToVersionWhichBreaksSomeNonCanaries() {
        // --- Setup
        DeploymentTester tester = new DeploymentTester();
        tester.upgrader().maintain();
        assertEquals("No system version: Nothing to do", 0, tester.buildSystem().jobs().size());

        Version version = Version.fromString("5.0"); // (lower than the hardcoded version in the config server client)
        tester.updateVersionStatus(version);

        tester.upgrader().maintain();
        assertEquals("No applications: Nothing to do", 0, tester.buildSystem().jobs().size());

        // Setup applications
        Application canary0 = tester.createAndDeploy("canary0", 0, "canary");
        Application canary1 = tester.createAndDeploy("canary1", 1, "canary");
        Application default0 = tester.createAndDeploy("default0",  2, "default");
        Application default1 = tester.createAndDeploy("default1",  3, "default");
        Application default2 = tester.createAndDeploy("default2",  4, "default");
        Application default3 = tester.createAndDeploy("default3",  5, "default");
        Application default4 = tester.createAndDeploy("default4",  6, "default");
        Application default5 = tester.createAndDeploy("default5",  7, "default");
        Application default6 = tester.createAndDeploy("default6",  8, "default");
        Application default7 = tester.createAndDeploy("default7",  9, "default");
        Application default8 = tester.createAndDeploy("default8", 10, "default");
        Application default9 = tester.createAndDeploy("default9", 11, "default");

        tester.upgrader().maintain();
        assertEquals("All already on the right version: Nothing to do", 0, tester.buildSystem().jobs().size());

        // --- A new version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        assertEquals("New system version: Should upgrade Canaries", 2, tester.buildSystem().jobs().size());
        tester.completeUpgrade(canary0, version, "canary");
        assertEquals(version, tester.configServerClientMock().lastPrepareVersion.get());

        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals("One canary pending; nothing else", 1, tester.buildSystem().jobs().size());

        tester.completeUpgrade(canary1, version, "canary");

        tester.updateVersionStatus(version);
        assertEquals(VespaVersion.Confidence.normal, tester.controller().versionStatus().systemVersion().get().confidence());
        tester.upgrader().maintain();
        assertEquals("Canaries done: Should upgrade defaults", 10, tester.buildSystem().jobs().size());

        tester.completeUpgrade(default0, version, "default");
        tester.completeUpgradeWithError(default1, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default2, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default3, version, "default", DeploymentJobs.JobType.systemTest);
        tester.completeUpgradeWithError(default4, version, "default", DeploymentJobs.JobType.systemTest);

        // > 40% and at least 4 failed - version is broken
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertEquals(VespaVersion.Confidence.broken, tester.controller().versionStatus().systemVersion().get().confidence());
        assertEquals("Upgrades are cancelled", 0, tester.buildSystem().jobs().size());
    }

    @Test
    public void testDeploymentAlreadyInProgressForUpgrade() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("us-east-3")
                .build();
        Version version = Version.fromString("5.0");
        tester.updateVersionStatus(version);

        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.productionUsEast3, app, applicationPackage, true);

        tester.upgrader().maintain();
        assertEquals("Application is on expected version: Nothing to do", 0,
                     tester.buildSystem().jobs().size());

        // New version is released
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());
        tester.upgrader().maintain();

        // system-test completes successfully
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);

        // staging-test fails multiple times, exhausts retries and failure is recorded
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, false);
        tester.buildSystem().takeJobsToRun();
        tester.clock().advance(Duration.ofMinutes(10));
        tester.notifyJobCompletion(DeploymentJobs.JobType.stagingTest, app, false);
        assertTrue("Retries exhausted", tester.buildSystem().jobs().isEmpty());
        assertTrue("Failure is recorded", tester.application(app.id()).deploymentJobs().hasFailures());
        assertTrue("Application has pending change", tester.application(app.id()).deploying().isPresent());

        // New version is released
        version = Version.fromString("5.2");
        tester.updateVersionStatus(version);
        assertEquals(version, tester.controller().versionStatus().systemVersion().get().versionNumber());

        // Upgrade is scheduled. system-tests starts, but does not complete
        tester.upgrader().maintain();
        assertTrue("Application still has failures", tester.application(app.id()).deploymentJobs().hasFailures());
        assertEquals(1, tester.buildSystem().jobs().size());
        tester.buildSystem().takeJobsToRun();

        // Upgrader runs again, nothing happens as there's already a job in progress for this change
        tester.upgrader().maintain();
        assertTrue("No more jobs triggered at this time", tester.buildSystem().jobs().isEmpty());
    }

    // TODO: Remove when corp-prod special casing is no longer needed
    @Test
    public void upgradesCanariesToControllerVersion() {
        DeploymentTester tester = new DeploymentTester();
        ApplicationPackage applicationPackage = new ApplicationPackageBuilder()
                .upgradePolicy("canary")
                .environment(Environment.prod)
                .region("corp-us-east-1")
                .build();

        Version version = Version.fromString("5.0"); // Lower version than controller (6.10)
        tester.updateVersionStatus(version);

        // Application is on 5.0
        Application app = tester.createApplication("app1", "tenant1", 1, 11L);
        tester.notifyJobCompletion(DeploymentJobs.JobType.component, app, true);
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.productionCorpUsEast1, app, applicationPackage, true);

        // Canary in prod.corp-us-east-1 is upgraded to controller version
        tester.upgrader().maintain();
        assertEquals("Upgrade started", 1, tester.buildSystem().jobs().size());
        assertEquals(Vtag.currentVersion, ((Change.VersionChange) tester.application(app.id()).deploying().get()).version());
        tester.deployAndNotify(DeploymentJobs.JobType.systemTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.stagingTest, app, applicationPackage, true);
        tester.deployAndNotify(DeploymentJobs.JobType.productionCorpUsEast1, app, applicationPackage, true);

        // System is upgraded to newer version, no upgrade triggered for canary as version is lower than controller
        version = Version.fromString("5.1");
        tester.updateVersionStatus(version);
        tester.upgrader().maintain();
        assertTrue("No more jobs triggered", tester.buildSystem().jobs().isEmpty());
    }

}
