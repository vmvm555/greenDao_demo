package de.greenrobot.dao.query;

import android.os.Process;
import android.util.SparseArray;

import java.lang.ref.WeakReference;

import de.greenrobot.dao.AbstractDao;

abstract class AbstractQueryData<T, Q extends AbstractQuery<T>> {
    final String sql;
    final AbstractDao<T, ?> dao;
    final String[] initialValues;
    //维护线程id和Query对象的集合
    final SparseArray<WeakReference<Q>> queriesForThreads;

    AbstractQueryData(AbstractDao<T, ?> dao, String sql, String[] initialValues) {
        this.dao = dao;
        this.sql = sql;
        this.initialValues = initialValues;
        queriesForThreads = new SparseArray<WeakReference<Q>>();
    }

    /** Just an optimized version, which performs faster if the current thread is already the query's owner thread. */
    Q forCurrentThread(Q query) {
        //当前线程==query的线程
        if (Thread.currentThread() == query.ownerThread) {
            //将查询参数值进行copy
            System.arraycopy(initialValues, 0, query.parameters, 0, initialValues.length);
            return query;
        } else {
            return forCurrentThread();
        }
    }
    //初始化线程id和Query对象关系的方法
    Q forCurrentThread() {
        int threadId = Process.myTid();
        if (threadId == 0) {
            // Workaround for Robolectric, always returns 0
            long id = Thread.currentThread().getId();
            if (id < 0 || id > Integer.MAX_VALUE) {
                throw new RuntimeException("Cannot handle thread ID: " + id);
            }
            threadId = (int) id;
        }
        synchronized (queriesForThreads) {
            //从map中获取ref,ref中包含Query
            WeakReference<Q> queryRef = queriesForThreads.get(threadId);
            Q query = queryRef != null ? queryRef.get() : null;
            if (query == null) {
                //遍历清除集合中没有值的弱引用对象
                gc();
                //创建query并扔进集合
                query = createQuery();
                queriesForThreads.put(threadId, new WeakReference<Q>(query));
            } else {
                System.arraycopy(initialValues, 0, query.parameters, 0, initialValues.length);
            }
            return query;
        }
    }

    abstract protected Q createQuery();
    //清除无数据的弱引用
    void gc() {
        synchronized (queriesForThreads) {
            for (int i = queriesForThreads.size() - 1; i >= 0; i--) {
                if (queriesForThreads.valueAt(i).get() == null) {
                    queriesForThreads.remove(queriesForThreads.keyAt(i));
                }
            }
        }
    }

}
