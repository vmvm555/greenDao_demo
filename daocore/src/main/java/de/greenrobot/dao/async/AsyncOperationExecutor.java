/*
 * Copyright (C) 2011-2015 Markus Junginger, greenrobot (http://greenrobot.de)
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
package de.greenrobot.dao.async;

import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import de.greenrobot.dao.DaoException;
import de.greenrobot.dao.DaoLog;
import de.greenrobot.dao.query.Query;

class AsyncOperationExecutor implements Runnable, Handler.Callback {

    private static ExecutorService executorService = Executors.newCachedThreadPool();

    private final BlockingQueue<AsyncOperation> queue;
    private volatile boolean executorRunning;
    private volatile int maxOperationCountToMerge;
    private volatile AsyncOperationListener listener;
    private volatile AsyncOperationListener listenerMainThread;
    private volatile int waitForMergeMillis;

    private int countOperationsEnqueued;
    private int countOperationsCompleted;

    private Handler handlerMainThread;
    private int lastSequenceNumber;

    AsyncOperationExecutor() {
        //创建阻塞集合
        queue = new LinkedBlockingQueue<AsyncOperation>();
        maxOperationCountToMerge = 50;
        waitForMergeMillis = 50;
    }

    /**
     * 扔进阻塞集合队列,并调动线程池,使其运行
     * @param operation
     */
    public void enqueue(AsyncOperation operation) {
        synchronized (this) {
            operation.sequenceNumber = ++lastSequenceNumber;
            queue.add(operation);
            countOperationsEnqueued++;
            if (!executorRunning) {
                executorRunning = true;
                executorService.execute(this);
            }
        }
    }

    public int getMaxOperationCountToMerge() {
        return maxOperationCountToMerge;
    }

    public void setMaxOperationCountToMerge(int maxOperationCountToMerge) {
        this.maxOperationCountToMerge = maxOperationCountToMerge;
    }

    public int getWaitForMergeMillis() {
        return waitForMergeMillis;
    }

    public void setWaitForMergeMillis(int waitForMergeMillis) {
        this.waitForMergeMillis = waitForMergeMillis;
    }

    public AsyncOperationListener getListener() {
        return listener;
    }

    public void setListener(AsyncOperationListener listener) {
        this.listener = listener;
    }

    public AsyncOperationListener getListenerMainThread() {
        return listenerMainThread;
    }

    public void setListenerMainThread(AsyncOperationListener listenerMainThread) {
        this.listenerMainThread = listenerMainThread;
    }

    public synchronized boolean isCompleted() {
        return countOperationsEnqueued == countOperationsCompleted;
    }

    /**
     * Waits until all enqueued operations are complete. If the thread gets interrupted, any
     * {@link InterruptedException} will be rethrown as a {@link DaoException}.
     */
    public synchronized void waitForCompletion() {
        while (!isCompleted()) {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new DaoException("Interrupted while waiting for all operations to complete", e);
            }
        }
    }

    /**
     * Waits until all enqueued operations are complete, but at most the given amount of milliseconds. If the thread
     * gets interrupted, any {@link InterruptedException} will be rethrown as a {@link DaoException}.
     *
     * @return true if operations completed in the given time frame.
     */
    public synchronized boolean waitForCompletion(int maxMillis) {
        if (!isCompleted()) {
            try {
                wait(maxMillis);
            } catch (InterruptedException e) {
                throw new DaoException("Interrupted while waiting for all operations to complete", e);
            }
        }
        return isCompleted();
    }

    @Override
    public void run() {
        try {
            try {
                while (true) {
                    AsyncOperation operation = queue.poll(1, TimeUnit.SECONDS);
                    if (operation == null) {
                        synchronized (this) {
                            // Check again, this time in synchronized to be in sync with enqueue(AsyncOperation)
                            operation = queue.poll();
                            if (operation == null) {
                                // set flag while still inside synchronized
                                executorRunning = false;
                                return;
                            }
                        }
                    }
                    //不为0,表示可以合并事务
                    if (operation.isMergeTx()) {
                        // 等待若干时间段,看看其他AsyncOperation有没有被扔进来
                        // Wait some ms for another operation to merge because a TX is expensive
                        AsyncOperation operation2 = queue.poll(waitForMergeMillis, TimeUnit.MILLISECONDS);
                        if (operation2 != null) {
                            //检查合并的双方是否有权限合并
                            if (operation.isMergeableWith(operation2)) {
                                //合并两个任务
                                mergeTxAndExecute(operation, operation2);
                            } else {
                                // Cannot merge, execute both
                                executeOperationAndPostCompleted(operation);
                                executeOperationAndPostCompleted(operation2);
                            }
                            continue;
                        }
                    }
                    //不用合并事务
                    executeOperationAndPostCompleted(operation);
                }
            } catch (InterruptedException e) {
                DaoLog.w(Thread.currentThread().getName() + " was interruppted", e);
            }
        } finally {
            executorRunning = false;
        }
    }


    /** Also checks for other operations in the queue that can be merged into the transaction. */
    private void mergeTxAndExecute(AsyncOperation operation1, AsyncOperation operation2) {
        ArrayList<AsyncOperation> mergedOps = new ArrayList<AsyncOperation>();
        mergedOps.add(operation1);
        mergedOps.add(operation2);

        SQLiteDatabase db = operation1.getDatabase();
        db.beginTransaction();
        boolean success = false;
        //不断获取新的对象进行合并
        try {
            for (int i = 0; i < mergedOps.size(); i++) {
                AsyncOperation operation = mergedOps.get(i);
                executeOperation(operation);
                //如果异常不为空,则视为失败,跳出循环
                if (operation.isFailed()) {
                    // Operation may still have changed the DB, roll back everything
                    break;
                }
                if (i == mergedOps.size() - 1) {
                    //如果是集合中最后一个对象的话,不断从阻塞序列中获取最新鲜的对象
                    AsyncOperation peekedOp = queue.peek();
                    //符合合并规则,小于最大的合并个数并且isMergeableWith返回true
                    if (i < maxOperationCountToMerge && operation.isMergeableWith(peekedOp)) {
                        AsyncOperation removedOp = queue.remove();
                        if (removedOp != peekedOp) {
                            // Paranoia check, should not occur unless threading is broken
                            throw new DaoException("Internal error: peeked op did not match removed op");
                        }
                        //添加进集合
                        mergedOps.add(removedOp);
                    } else {
                        //不需要合并了,直接让事务成功
                        // No more ops in the queue to merge, finish it
                        db.setTransactionSuccessful();
                        success = true;
                        break;
                    }
                }
            }
        } finally {
            try {
                //结束事务
                db.endTransaction();
            } catch (RuntimeException e) {
                DaoLog.i("Async transaction could not be ended, success so far was: " + success, e);
                success = false;
            }
        }
        if (success) {
            int mergedCount = mergedOps.size();
            for (AsyncOperation asyncOperation : mergedOps) {
                asyncOperation.mergedOperationsCount = mergedCount;
                //调用每个AsyncOperation对象的handleOperationCompleted方法,使任务完成,成功回调
                handleOperationCompleted(asyncOperation);
            }
        } else {
            DaoLog.i("Reverted merged transaction because one of the operations failed. Executing operations one by " +
                    "one instead...");
            for (AsyncOperation asyncOperation : mergedOps) {
                //任务失败,进行重置
                asyncOperation.reset();
                //单个,单个的重新执行
                executeOperationAndPostCompleted(asyncOperation);
            }
        }
    }

    /**
     * 任务完成后,做些收尾工作,并通过handle发送到主线程
     * @param operation
     */
    private void handleOperationCompleted(AsyncOperation operation) {
        operation.setCompleted();

        AsyncOperationListener listenerToCall = listener;
        if (listenerToCall != null) {
            //调用回调函数
            listenerToCall.onAsyncOperationCompleted(operation);
        }
        // 通过handle发送到主线程
        if (listenerMainThread != null) {
            if (handlerMainThread == null) {
                handlerMainThread = new Handler(Looper.getMainLooper(), this);
            }
            Message msg = handlerMainThread.obtainMessage(1, operation);
            handlerMainThread.sendMessage(msg);
        }
        synchronized (this) {
            countOperationsCompleted++;
            if (countOperationsCompleted == countOperationsEnqueued) {
                //让在当前对象中wait的线程全部苏醒
                notifyAll();
            }
        }
    }
    //执行操作,并且发送完成
    private void executeOperationAndPostCompleted(AsyncOperation operation) {
        executeOperation(operation);
        handleOperationCompleted(operation);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeOperation(AsyncOperation operation) {
        operation.timeStarted = System.currentTimeMillis();
        try {
            switch (operation.type) {
                case Delete:
                    operation.dao.delete(operation.parameter);
                    break;
                case DeleteInTxIterable:
                    operation.dao.deleteInTx((Iterable<Object>) operation.parameter);
                    break;
                case DeleteInTxArray:
                    operation.dao.deleteInTx((Object[]) operation.parameter);
                    break;
                case Insert:
                    operation.dao.insert(operation.parameter);
                    break;
                case InsertInTxIterable:
                    operation.dao.insertInTx((Iterable<Object>) operation.parameter);
                    break;
                case InsertInTxArray:
                    operation.dao.insertInTx((Object[]) operation.parameter);
                    break;
                case InsertOrReplace:
                    operation.dao.insertOrReplace(operation.parameter);
                    break;
                case InsertOrReplaceInTxIterable:
                    operation.dao.insertOrReplaceInTx((Iterable<Object>) operation.parameter);
                    break;
                case InsertOrReplaceInTxArray:
                    operation.dao.insertOrReplaceInTx((Object[]) operation.parameter);
                    break;
                case Update:
                    operation.dao.update(operation.parameter);
                    break;
                case UpdateInTxIterable:
                    operation.dao.updateInTx((Iterable<Object>) operation.parameter);
                    break;
                case UpdateInTxArray:
                    operation.dao.updateInTx((Object[]) operation.parameter);
                    break;
                case TransactionRunnable:
                    executeTransactionRunnable(operation);
                    break;
                case TransactionCallable:
                    executeTransactionCallable(operation);
                    break;
                case QueryList:
                    operation.result = ((Query) operation.parameter).forCurrentThread().list();
                    break;
                case QueryUnique:
                    operation.result = ((Query) operation.parameter).forCurrentThread().unique();
                    break;
                case DeleteByKey:
                    operation.dao.deleteByKey(operation.parameter);
                    break;
                case DeleteAll:
                    operation.dao.deleteAll();
                    break;
                case Load:
                    operation.result = operation.dao.load(operation.parameter);
                    break;
                case LoadAll:
                    operation.result = operation.dao.loadAll();
                    break;
                case Count:
                    operation.result = operation.dao.count();
                    break;
                case Refresh:
                    operation.dao.refresh(operation.parameter);
                    break;
                default:
                    throw new DaoException("Unsupported operation: " + operation.type);
            }
        } catch (Throwable th) {
            //出现任何异常,直接捕获赋值
            operation.throwable = th;
        }
        operation.timeCompleted = System.currentTimeMillis();
        // Do not set it to completed here because it might be a merged TX
    }
    //在线程池中开启事务运行
    private void executeTransactionRunnable(AsyncOperation operation) {
        SQLiteDatabase db = operation.getDatabase();
        db.beginTransaction();
        try {
            ((Runnable) operation.parameter).run();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @SuppressWarnings("unchecked")
    private void executeTransactionCallable(AsyncOperation operation) throws Exception {
        SQLiteDatabase db = operation.getDatabase();
        db.beginTransaction();
        try {
            operation.result = ((Callable<Object>) operation.parameter).call();
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * 主线程当中调用
     * @param msg
     * @return
     */
    @Override
    public boolean handleMessage(Message msg) {
        AsyncOperationListener listenerToCall = listenerMainThread;
        if (listenerToCall != null) {
            listenerToCall.onAsyncOperationCompleted((AsyncOperation) msg.obj);
        }
        return false;
    }

}
