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
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCStatement;
import org.jkiss.dbeaver.model.impl.jdbc.cache.JDBCObjectWithParentCache;

import java.sql.SQLException;

public class TableTriggerCache extends JDBCObjectWithParentCache<CubridStructContainer, CubridTableBase, CubridTrigger> {

    TableTriggerCache(TableCache tableCache) {
        super(tableCache, CubridTableBase.class, "OWNER", "TRIGGER_NAME");
    }

    @NotNull
    @Override
    protected JDBCStatement prepareObjectsStatement(@NotNull JDBCSession session, @NotNull CubridStructContainer cubridStructContainer, @Nullable CubridTableBase tableBase) throws SQLException {
        return cubridStructContainer.getDataSource().getMetaModel().prepareTableTriggersLoadStatement(session, cubridStructContainer, tableBase);
    }

    @Nullable
    @Override
    protected CubridTrigger fetchObject(@NotNull JDBCSession session, @NotNull CubridStructContainer cubridStructContainer, @NotNull CubridTableBase cubridTableBase, String triggerName, @NotNull JDBCResultSet resultSet) throws SQLException, DBException {
        return cubridStructContainer.getDataSource().getMetaModel().createTableTriggerImpl(session, cubridStructContainer, cubridTableBase, triggerName, resultSet);
    }
}
