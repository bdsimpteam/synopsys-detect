package com.synopsys.integration.detectable.detectables.clang.unit;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.synopsys.integration.detectable.detectable.executable.ExecutableRunnerException;
import com.synopsys.integration.detectable.detectables.clang.packagemanager.ClangPackageManagerInfoFactory;
import com.synopsys.integration.detectable.detectables.clang.packagemanager.PackageDetails;
import com.synopsys.integration.detectable.detectables.clang.packagemanager.resolver.RpmPackageManagerResolver;

public class RpmPackageManagerTest {

    @Test
    public void testValid() throws ExecutableRunnerException {
        final StringBuilder sb = new StringBuilder();
        sb.append("glibc-headers-2.17-222.el7.x86_64\n");
        final String pkgMgrOwnedByOutput = sb.toString();

        final RpmPackageManagerResolver pkgMgr = new RpmPackageManagerResolver();
        final List<PackageDetails> pkgs = pkgMgr.resolvePackages(new ClangPackageManagerInfoFactory().rpm(), null, null, pkgMgrOwnedByOutput);

        assertEquals(1, pkgs.size());
        assertEquals("glibc-headers", pkgs.get(0).getPackageName());
        assertEquals("2.17-222.el7", pkgs.get(0).getPackageVersion());
        assertEquals("x86_64", pkgs.get(0).getPackageArch());
    }

    @Test
    public void testInValid() throws ExecutableRunnerException {
        final StringBuilder sb = new StringBuilder();
        sb.append("garbage\n");
        sb.append("nonsense\n");
        sb.append("file /opt/hub-detect/clang-repos/hello_world/hello_world.cpp is not owned by any package\n");
        final String pkgMgrOwnedByOutput = sb.toString();

        final RpmPackageManagerResolver pkgMgr = new RpmPackageManagerResolver();
        final List<PackageDetails> pkgs = pkgMgr.resolvePackages(new ClangPackageManagerInfoFactory().rpm(), null, null, pkgMgrOwnedByOutput);

        assertEquals(0, pkgs.size());
    }

}
