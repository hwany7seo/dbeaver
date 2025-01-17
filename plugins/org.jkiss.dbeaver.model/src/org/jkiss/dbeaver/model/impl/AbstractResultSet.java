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
package org.jkiss.dbeaver.model.impl;

import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.data.DBDValueMeta;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCResultSet;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.DBCStatement;

/**
 * Abstract result set
 */
public abstract class AbstractResultSet<SESSION extends DBCSession, STATEMENT extends DBCStatement> implements DBCResultSet {

    private static final Log log = Log.getLog(AbstractResultSet.class);

    protected final SESSION session;
    protected STATEMENT statement;

    protected AbstractResultSet(SESSION session, STATEMENT statement) {
        this.session = session;
        this.statement = statement;
    }


    @Override
    public SESSION getSession() {
        return session;
    }

    @Override
    public STATEMENT getSourceStatement() {
        return statement;
    }

    @Override
    public Object getFeature(String name) {
        return null;
    }

    @Override
    public DBDValueMeta getAttributeValueMeta(int index) throws DBCException {
        return null;
    }

    @Override
    public DBDValueMeta getRowMeta() throws DBCException {
        return null;
    }

    @Override
    public String getResultSetName() throws DBCException {
        return null;
    }

    protected void beforeFetch() {
    }

    protected void afterFetch() {
    }

}
