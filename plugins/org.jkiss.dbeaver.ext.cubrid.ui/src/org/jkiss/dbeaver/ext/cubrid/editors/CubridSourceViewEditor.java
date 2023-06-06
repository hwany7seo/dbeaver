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

package org.jkiss.dbeaver.ext.cubrid.editors;

import org.jkiss.dbeaver.ext.cubrid.model.CubridSQLDialect;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectWithScript;
import org.jkiss.dbeaver.ui.editors.sql.SQLSourceViewer;

/**
 * CubridSourceViewEditor
 */
public class CubridSourceViewEditor<T extends DBPScriptObject & DBSObject> extends SQLSourceViewer<T> {

    public CubridSourceViewEditor() {
    }

    @Override
    protected boolean isReadOnly() {
        return !(getSourceObject() instanceof DBSObjectWithScript);
    }

    @Override
    protected void setSourceText(DBRProgressMonitor monitor, String sourceText) {
        boolean supportsDelimitersInViews = true;
        if (getSQLDialect() instanceof CubridSQLDialect) {
            supportsDelimitersInViews = ((CubridSQLDialect) getSQLDialect()).supportsDelimiterAfterViews();
        }
        getInputPropertySource().setPropertyValue(monitor, "objectDefinitionText",
            supportsDelimitersInViews ? sourceText : SQLUtils.removeQueryDelimiter(getSQLDialect(), sourceText));
    }

}

