# Database Migrations

This project uses **Liquibase** for database schema versioning and migrations.

## Overview

- **Migration Tool**: Liquibase
- **Database**: H2 (file-based at `./data/playbot`)
- **Migration Location**: `src/main/resources/db/changelog/`
- **Auto-run**: Migrations execute automatically on application startup

## Migration Files

### Master Changelog
- `db/changelog/db.changelog-master.xml` - Main entry point, includes all changesets

### Changesets (in execution order)

#### Baseline Schema (000-*)
- `000-initial-schema.xml` - Initial database schema
  - Creates `user_cooldowns` table
  - Creates `qotd_configs` table
  - Creates `qotd_questions` table
  - Creates `qotd_submissions` table
  - Uses `MARK_RAN` strategy to skip if tables exist (baseline for existing databases)

#### Schema Changes (001-*)
- `001-add-qotd-auto-approve-and-author-columns.xml` - QOTD enhancements
  - Adds `auto_approve` column to `qotd_configs`
  - Adds `author_user_id` and `author_username` columns to `qotd_questions`

## How to Add a New Migration

1. **Create a new changeset file** in `src/main/resources/db/changelog/changes/`
   - Name format: `XXX-description.xml` (increment number, e.g., `002-add-feature.xml`)

2. **Add the changeset** to `db.changelog-master.xml`:
   ```xml
   <include file="db/changelog/changes/002-your-new-migration.xml"/>
   ```

3. **Test locally** before deploying:
   ```bash
   ./build.sh
   ```

4. **Deploy** - migrations run automatically on startup:
   ```bash
   ./deploy.sh
   ```

## Example Migration Template

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <changeSet id="002-add-new-column" author="your-name">
        <comment>Add description of what this does</comment>
        
        <addColumn tableName="your_table">
            <column name="new_column" type="varchar(255)">
                <constraints nullable="true"/>
            </column>
        </addColumn>
    </changeSet>

</databaseChangeLog>
```

## Safe Migration Practices

### Adding NOT NULL Columns to Existing Tables

When adding a NOT NULL column to a table with existing data:

```xml
<!-- Step 1: Add column as nullable -->
<addColumn tableName="your_table">
    <column name="new_column" type="boolean" defaultValueBoolean="false">
        <constraints nullable="true"/>
    </column>
</addColumn>

<!-- Step 2: Update existing rows -->
<update tableName="your_table">
    <column name="new_column" valueBoolean="false"/>
    <where>new_column IS NULL</where>
</update>

<!-- Step 3: Make column non-nullable -->
<addNotNullConstraint tableName="your_table" columnName="new_column" defaultNullValue="false"/>
```

### Using Preconditions

To make migrations idempotent (safe to run multiple times):

```xml
<changeSet id="example" author="your-name">
    <preConditions onFail="MARK_RAN">
        <not>
            <columnExists tableName="your_table" columnName="new_column"/>
        </not>
    </preConditions>
    
    <!-- your migration here -->
</changeSet>
```

## Configuration

From `application.properties`:

```properties
# Liquibase enabled
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.xml

# Hibernate validation only (Liquibase manages schema)
spring.jpa.hibernate.ddl-auto=validate
```

## Migration History

Liquibase tracks applied migrations in the `DATABASECHANGELOG` table. Each changeset is recorded with:
- ID
- Author
- Filename
- Timestamp
- Checksum (for detecting changes)

## Troubleshooting

### View migration history
Connect to H2 database and query:
```sql
SELECT * FROM DATABASECHANGELOG ORDER BY DATEEXECUTED;
```

### Failed migration
If a migration fails:
1. Check application logs for the error
2. Fix the issue in the changeset file
3. Liquibase will retry on next startup

### Rollback (manual process)
Liquibase supports rollback, but we use forward-only migrations:
- Create a new changeset to undo changes
- Never modify existing changesets that have been deployed

## Best Practices

1. ✅ **Never modify deployed changesets** - Always create new ones
2. ✅ **Test locally first** - Run `./build.sh` before deploying
3. ✅ **Use descriptive IDs** - Make it clear what each changeset does
4. ✅ **Add comments** - Explain why the change is needed
5. ✅ **Handle existing data** - Use the 3-step process for NOT NULL columns
6. ✅ **Use preconditions** - Make migrations idempotent when possible
7. ✅ **Commit changesets with code** - Keep schema and code in sync
