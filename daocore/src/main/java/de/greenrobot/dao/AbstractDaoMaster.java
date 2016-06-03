/*
 * Copyright (C) 2011 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.greenrobot.dao;

import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

import de.greenrobot.dao.identityscope.IdentityScopeType;
import de.greenrobot.dao.internal.DaoConfig;

/**
 * The master of dao will guide you: start dao sessions with the master.
 * 
 * @author Markus
 */
public abstract class AbstractDaoMaster {
    protected final SQLiteDatabase db;
    protected final int schemaVersion;
    protected final Map<Class<? extends AbstractDao<?, ?>>, DaoConfig> daoConfigMap;

    public AbstractDaoMaster(SQLiteDatabase db, int schemaVersion) {
        this.db = db;
        this.schemaVersion = schemaVersion;
        //创建集合,用来存储Dao文件字节码和字节码解析出的信息:DaoConfig对象
        daoConfigMap = new HashMap<Class<? extends AbstractDao<?, ?>>, DaoConfig>();
    }

    protected void registerDaoClass(Class<? extends AbstractDao<?, ?>> daoClass) {
        //创建DaoConfig对象,并在构造方法中解析
        DaoConfig daoConfig = new DaoConfig(db, daoClass);
        //扔进集合
        daoConfigMap.put(daoClass, daoConfig);
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** Gets the SQLiteDatabase for custom database access. Not needed for greenDAO entities. */
    public SQLiteDatabase getDatabase() {
        return db;
    }

    public abstract AbstractDaoSession newSession();

    public abstract AbstractDaoSession newSession(IdentityScopeType type);
}
