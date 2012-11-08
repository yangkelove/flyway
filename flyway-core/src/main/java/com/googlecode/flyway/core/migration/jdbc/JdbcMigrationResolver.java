/**
 * Copyright (C) 2010-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.flyway.core.migration.jdbc;

import com.googlecode.flyway.core.api.MigrationType;
import com.googlecode.flyway.core.api.MigrationVersion;
import com.googlecode.flyway.core.api.migration.MigrationChecksumProvider;
import com.googlecode.flyway.core.api.migration.MigrationInfoProvider;
import com.googlecode.flyway.core.api.migration.jdbc.JdbcMigration;
import com.googlecode.flyway.core.exception.FlywayException;
import com.googlecode.flyway.core.migration.MigrationInfoHelper;
import com.googlecode.flyway.core.migration.MigrationResolver;
import com.googlecode.flyway.core.migration.ResolvedMigration;
import com.googlecode.flyway.core.util.ClassUtils;
import com.googlecode.flyway.core.util.Pair;
import com.googlecode.flyway.core.util.StringUtils;
import com.googlecode.flyway.core.util.scanner.ClassPathScanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Migration resolver for Jdbc migrations. The classes must have a name like V1 or V1_1_3 or V1__Description
 * or V1_1_3__Description.
 */
public class JdbcMigrationResolver implements MigrationResolver {
    /**
     * The base package on the classpath where to migrations are located.
     */
    private final String basePackage;

    /**
     * Creates a new instance.
     *
     * @param basePackage The base package on the classpath where to migrations are located.
     */
    public JdbcMigrationResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public List<ResolvedMigration> resolveMigrations() {
        List<ResolvedMigration> migrations = new ArrayList<ResolvedMigration>();

        try {
            Class<?>[] classes = new ClassPathScanner().scanForClasses(basePackage, JdbcMigration.class);
            for (Class<?> clazz : classes) {
                JdbcMigration jdbcMigration = (JdbcMigration) ClassUtils.instantiate(clazz.getName());

                ResolvedMigration migrationInfo = extractMigrationInfo(jdbcMigration);
                migrationInfo.setPhysicalLocation(ClassUtils.getLocationOnDisk(clazz));
                migrationInfo.setExecutor(new JdbcMigrationExecutor(jdbcMigration));

                migrations.add(migrationInfo);
            }
        } catch (Exception e) {
            throw new FlywayException("Unable to resolve Jdbc Java migrations in location: " + basePackage, e);
        }

        Collections.sort(migrations);
        return migrations;
    }

    /**
     * Extracts the migration info from this migration.
     *
     * @param jdbcMigration The migration to analyse.
     * @return The migration info.
     */
    /* private -> testing */ ResolvedMigration extractMigrationInfo(JdbcMigration jdbcMigration) {
        Integer checksum = null;
        if (jdbcMigration instanceof MigrationChecksumProvider) {
            MigrationChecksumProvider checksumProvider = (MigrationChecksumProvider) jdbcMigration;
            checksum = checksumProvider.getChecksum();
        }

        MigrationVersion version;
        String description;
        if (jdbcMigration instanceof MigrationInfoProvider) {
            MigrationInfoProvider infoProvider = (MigrationInfoProvider) jdbcMigration;
            version = infoProvider.getVersion();
            description = infoProvider.getDescription();
            if (!StringUtils.hasText(description)) {
                throw new FlywayException("Missing description for migration " + version);
            }
        } else {
            Pair<MigrationVersion,String> info =
                    MigrationInfoHelper.extractVersionAndDescription(ClassUtils.getShortName(jdbcMigration.getClass()), "V", "");
            version = info.getLeft();
            description = info.getRight();
        }

        String script = jdbcMigration.getClass().getName();


        ResolvedMigration resolvedMigration = new ResolvedMigration();
        resolvedMigration.setVersion(version);
        resolvedMigration.setDescription(description);
        resolvedMigration.setScript(script);
        resolvedMigration.setChecksum(checksum);
        resolvedMigration.setType(MigrationType.JDBC);
        return resolvedMigration;
    }
}
