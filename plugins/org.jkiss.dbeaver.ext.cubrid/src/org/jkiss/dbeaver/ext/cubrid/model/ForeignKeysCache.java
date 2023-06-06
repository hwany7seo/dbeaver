/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.ext.cubrid.model;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.CubridConstants;
import org.jkiss.dbeaver.ext.cubrid.model.meta.CubridMetaModel;
import org.jkiss.dbeaver.ext.cubrid.model.meta.CubridMetaModelForeignKeyFetcher;
import org.jkiss.dbeaver.ext.cubrid.model.meta.CubridMetaObject;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCConstants;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCCompositeCache;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSEntityAttributeRef;
import org.jkiss.dbeaver.model.struct.DBSEntityConstraintType;
import org.jkiss.dbeaver.model.struct.DBSEntityReferrer;
import org.jkiss.dbeaver.model.struct.rdb.DBSForeignKeyDeferability;
import org.jkiss.dbeaver.model.struct.rdb.DBSForeignKeyModifyRule;
import org.jkiss.utils.CommonUtils;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;

/**
* Foreign key cache
*/
class ForeignKeysCache extends JDBCCompositeCache<CubridStructContainer, CubridTableBase, CubridTableForeignKey, CubridTableForeignKeyColumnTable> {

    private final Map<String, CubridUniqueKey> pkMap = new HashMap<>();
    private final CubridMetaObject foreignKeyObject;
    private int fkIndex;

    ForeignKeysCache(TableCache tableCache)
    {
        super(
            tableCache,
            CubridTableBase.class,
            CubridUtils.getColumn(tableCache.getDataSource(), CubridConstants.OBJECT_FOREIGN_KEY, JDBCConstants.FKTABLE_NAME),
            CubridUtils.getColumn(tableCache.getDataSource(), CubridConstants.OBJECT_FOREIGN_KEY, JDBCConstants.FK_NAME));
        foreignKeyObject = tableCache.getDataSource().getMetaObject(CubridConstants.OBJECT_FOREIGN_KEY);
        fkIndex = 1;
    }

    @Override
    public void clearCache()
    {
        pkMap.clear();
        super.clearCache();
    }

    @NotNull
    @Override
    protected JDBCStatement prepareObjectsStatement(JDBCSession session, CubridStructContainer owner, CubridTableBase forParent)
        throws SQLException
    {
        return owner.getDataSource().getMetaModel().prepareForeignKeysLoadStatement(session, owner, forParent);
    }

    @Nullable
    @Override
    protected CubridTableForeignKey fetchObject(JDBCSession session, CubridStructContainer owner, CubridTableBase parent, String fkName, JDBCResultSet dbResult)
        throws SQLException, DBException
    {
        String pkTableCatalog = CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKTABLE_CAT);
        String pkTableSchema = CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKTABLE_SCHEM);
        boolean trimNames = owner.getDataSource().getMetaModel().isTrimObjectNames();
        String pkTableName = trimNames ?
            CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKTABLE_NAME)
            : CubridUtils.safeGetString(foreignKeyObject, dbResult, JDBCConstants.PKTABLE_NAME);
        String fkTableCatalog = CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.FKTABLE_CAT);
        String fkTableSchema = CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.FKTABLE_SCHEM);

        int keySeq = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.KEY_SEQ);
        String pkName = trimNames ?
            CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PK_NAME)
            : CubridUtils.safeGetString(foreignKeyObject, dbResult, JDBCConstants.PK_NAME);

        DBSForeignKeyModifyRule deleteRule;
        DBSForeignKeyModifyRule updateRule;
        DBSForeignKeyDeferability deferability;

        CubridMetaModel metaModel = owner.getDataSource().getMetaModel();

        if (metaModel instanceof CubridMetaModelForeignKeyFetcher) {
            CubridMetaModelForeignKeyFetcher foreignKeyFetcher = (CubridMetaModelForeignKeyFetcher) metaModel;
            deleteRule = foreignKeyFetcher.fetchDeleteRule(foreignKeyObject, dbResult);
            updateRule = foreignKeyFetcher.fetchUpdateRule(foreignKeyObject, dbResult);
            deferability = foreignKeyFetcher.fetchDeferability(foreignKeyObject, dbResult);
        } else {
            int updateRuleNum = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.UPDATE_RULE);
            int deleteRuleNum = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.DELETE_RULE);
            int deferabilityNum = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.DEFERRABILITY);

            deleteRule = JDBCUtils.getCascadeFromNum(deleteRuleNum);
            updateRule = JDBCUtils.getCascadeFromNum(updateRuleNum);
            switch (deferabilityNum) {
                case DatabaseMetaData.importedKeyInitiallyDeferred: deferability = DBSForeignKeyDeferability.INITIALLY_DEFERRED; break;
                case DatabaseMetaData.importedKeyInitiallyImmediate: deferability = DBSForeignKeyDeferability.INITIALLY_IMMEDIATE; break;
                case DatabaseMetaData.importedKeyNotDeferrable: deferability = DBSForeignKeyDeferability.NOT_DEFERRABLE; break;
                default: deferability = DBSForeignKeyDeferability.UNKNOWN; break;
            }
        }

        if (pkTableName == null) {
            log.debug("Null PK table name");
            return null;
        }
        String pkTableFullName = DBUtils.getSimpleQualifiedName(pkTableCatalog, pkTableSchema, pkTableName);
        CubridTableBase pkTable = parent.getDataSource().findTable(session.getProgressMonitor(), pkTableCatalog, pkTableSchema, pkTableName);
        if (pkTable == null) {
            // Try to use FK catalog/schema
            pkTable = parent.getDataSource().findTable(session.getProgressMonitor(), fkTableCatalog, fkTableSchema, pkTableName);
            if (pkTable == null) {
                log.warn("Can't find PK table " + pkTableName);
                return null;
            } else {
                log.debug("PK table " + pkTableFullName + " was taken from FK container.");
            }
        }

        // Find PK
        DBSEntityReferrer pk = null;
        if (!CommonUtils.isEmpty(pkName)) {
            pk = DBUtils.findObject(pkTable.getConstraints(session.getProgressMonitor()), pkName);
            if (pk == null) {
                log.debug("Unique key '" + pkName + "' not found in table " + pkTable.getFullyQualifiedName(DBPEvaluationContext.DDL) + " for foreign key " + fkName + " in table " + parent.getName());
            }
        }
        if (pk == null) {
            String pkColumnName = trimNames ?
                CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKCOLUMN_NAME)
                : CubridUtils.safeGetString(foreignKeyObject, dbResult, JDBCConstants.PKCOLUMN_NAME);
            CubridTableColumn pkColumn = pkTable.getAttribute(session.getProgressMonitor(), pkColumnName);
            if (pkColumn == null) {
                log.warn("Can't find PK table " + pkTable.getFullyQualifiedName(DBPEvaluationContext.DDL) + " column " + pkColumnName);
                return null;
            }

            Collection<CubridUniqueKey> uniqueKeys = pkTable.getConstraints(session.getProgressMonitor());
            if (uniqueKeys != null) {
                for (CubridUniqueKey pkConstraint : uniqueKeys) {
                    if (pkConstraint.getConstraintType().isUnique() && DBUtils.getConstraintAttribute(session.getProgressMonitor(), pkConstraint, pkColumn) != null) {
                        pk = pkConstraint;
                        break;
                    }
                }
            }
            if (pk == null) {
                // No PK. Let's try unique indexes
                Collection<? extends CubridTableIndex> indexes = pkTable.getIndexes(session.getProgressMonitor());
                if (indexes != null) {
                    for (CubridTableIndex index : indexes) {
                        if (index.isUnique() && DBUtils.getConstraintAttribute(session.getProgressMonitor(), index, pkColumn) != null) {
                            pk = index;
                            break;
                        }
                    }
                }
            }
            if (pk == null) {
                log.warn("Can't find unique key for table " + pkTable.getFullyQualifiedName(DBPEvaluationContext.DDL) + " column " + pkColumn.getName() + ". Making fake one.");
                CubridUniqueKey fakePk;
                // Too bad. But we have to create new fake PK for this FK
                if (pkName == null) {
                    pkName = "primary_key";
                }
                String pkFullName = pkTable.getFullyQualifiedName(DBPEvaluationContext.DDL) + "." + pkName;
                fakePk = pkMap.get(pkFullName);
                if (fakePk == null) {
                    fakePk = pkTable.getDataSource().getMetaModel().createConstraintImpl(pkTable, pkName,  DBSEntityConstraintType.PRIMARY_KEY, dbResult, true);
                    pkMap.put(pkFullName, fakePk);
                    // Add this fake constraint to it's owner
                    fakePk.getTable().addUniqueKey(fakePk);
                }
                fakePk.addColumn(new CubridTableConstraintColumn(fakePk, pkColumn, keySeq));
                pk = fakePk;
            }
        }
        if (CommonUtils.isEmpty(fkName)) {
            // [JDBC] Some drivers return empty foreign key names
            fkName = parent.getName().toUpperCase() + "_FK_" + pkTable.getName().toUpperCase(Locale.ENGLISH);
        }
        return owner.getDataSource().getMetaModel().createTableForeignKeyImpl(parent, fkName, null, pk, deleteRule, updateRule, deferability, true);
    }

    @Nullable
    @Override
    protected CubridTableForeignKeyColumnTable[] fetchObjectRow(
        JDBCSession session,
        CubridTableBase parent, CubridTableForeignKey foreignKey, JDBCResultSet dbResult)
        throws SQLException, DBException
    {
        boolean trimNames = parent.getDataSource().getMetaModel().isTrimObjectNames();
        String pkColumnName = trimNames ?
            CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKCOLUMN_NAME)
            : CubridUtils.safeGetString(foreignKeyObject, dbResult, JDBCConstants.PKCOLUMN_NAME);
        DBSEntityReferrer referencedConstraint = foreignKey.getReferencedConstraint();
        if (referencedConstraint == null) {
            log.warn("Null reference constraint in FK '" + foreignKey.getFullyQualifiedName(DBPEvaluationContext.DDL) + "'");
            return null;
        }
        DBSEntityAttributeRef pkColumn = DBUtils.getConstraintAttribute(
            session.getProgressMonitor(),
            referencedConstraint,
            pkColumnName);
        if (pkColumn == null) {
            log.warn("Can't find PK table " + DBUtils.getObjectFullName(referencedConstraint.getParentObject(), DBPEvaluationContext.DML) + " column " + pkColumnName);
            return null;
        }
        int keySeq = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.KEY_SEQ);

        String fkColumnName = trimNames ?
            CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.FKCOLUMN_NAME)
            : CubridUtils.safeGetString(foreignKeyObject, dbResult, JDBCConstants.FKCOLUMN_NAME);
        if (CommonUtils.isEmpty(fkColumnName)) {
            log.warn("Empty FK column for table " + foreignKey.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " PK column " + pkColumnName);
            return null;
        }
        CubridTableColumn fkColumn = foreignKey.getTable().getAttribute(session.getProgressMonitor(), fkColumnName);
        if (fkColumn == null) {
            log.warn("Can't find FK table " + foreignKey.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " column " + fkColumnName);
            return null;
        }

        return new CubridTableForeignKeyColumnTable[] {
            new CubridTableForeignKeyColumnTable(foreignKey, fkColumn, keySeq, (CubridTableColumn)pkColumn.getAttribute()) };
    }

    @Override
    protected void cacheChildren(DBRProgressMonitor monitor, CubridTableForeignKey foreignKey, List<CubridTableForeignKeyColumnTable> rows)
    {
        foreignKey.setColumns(monitor, rows);
        fkIndex = 1;
    }

    @Override
    protected String getDefaultObjectName(JDBCResultSet dbResult, String parentName) {
        final String pkTableName = CubridUtils.safeGetStringTrimmed(foreignKeyObject, dbResult, JDBCConstants.PKTABLE_NAME);
        int keySeq = CubridUtils.safeGetInt(foreignKeyObject, dbResult, JDBCConstants.KEY_SEQ);
        String fkName = "FK_" + parentName + "_" + pkTableName;
        if (fkIndex > 1 && keySeq == 1) {
            fkName += "_" + fkIndex;
        }
        fkIndex++;
        return fkName;
    }
}
